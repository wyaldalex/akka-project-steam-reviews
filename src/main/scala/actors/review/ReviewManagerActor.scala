package dev.galre.josue.akkaProject
package actors.review

import actors.ReviewController
import actors.review.ReviewActor.ReviewState

import akka.actor.{ ActorLogging, Props }
import akka.persistence._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }


object ReviewManagerActor {

  // reviews
  case class ReviewManager(
    var reviewCount: BigInt = BigInt(0),
    reviews:         mutable.AnyRefMap[BigInt, ReviewController]
  )

  val reviewManagerSnapshotInterval = 1000

  // commands
  case class CreateReviewFromCSV(review: ReviewState)

  // events
  case class ReviewActorCreated(id: BigInt)

  case class ReviewActorDeleted(id: BigInt)

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new ReviewManagerActor())
}

class ReviewManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import ReviewActor._
  import ReviewManagerActor._

  var reviewManagerState: ReviewManager = ReviewManager(reviews = mutable.AnyRefMap())

  override def persistenceId: String = "steam-review-manager"

  def isReviewAvailable(id: BigInt): Boolean =
    reviewManagerState.reviews.contains(id) && !reviewManagerState.reviews(id).isDisabled

  def createActorName(steamReviewId: BigInt): String = s"steam-review-$steamReviewId"

  def notFoundExceptionCreator[T](id: BigInt): Try[T] =
    Failure(NotFoundException(s"A review with the id $id couldn't be found"))

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % reviewManagerSnapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(reviewManagerState)

  override def receiveCommand: Receive = {
    case createCommand: CreateReview =>
      val steamReviewId    = reviewManagerState.reviewCount
      val reviewActorName  = createActorName(steamReviewId)
      val reviewActor      = context.actorOf(
        ReviewActor.props(steamReviewId),
        reviewActorName
      )
      val controlledReview = ReviewController(reviewActor)

      persist(ReviewActorCreated(steamReviewId)) { _ =>
        reviewManagerState = reviewManagerState.copy(
          reviewCount = reviewManagerState.reviewCount + 1,
          reviews = reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
        )

        tryToSaveSnapshot()

        reviewActor.forward(createCommand)
      }

    case getCommand @ GetReviewInfo(id) =>
      if (isReviewAvailable(id))
        reviewManagerState.reviews(id).actor.forward(getCommand)
      else
        sender() ! GetReviewInfoResponse(notFoundExceptionCreator(id))

    case updateCommand @ UpdateReview(review) =>
      if (isReviewAvailable(review.reviewId))
        reviewManagerState.reviews(review.reviewId).actor.forward(updateCommand)
      else
        sender() ! ReviewUpdatedResponse(notFoundExceptionCreator(review.reviewId))

    case DeleteReview(id) =>
      if (isReviewAvailable(id))
        persist(ReviewActorDeleted(id)) { _ =>
          reviewManagerState.reviews(id).isDisabled = true
          context.stop(reviewManagerState.reviews(id).actor)

          tryToSaveSnapshot()

          sender() ! ReviewDeletedResponse(Success(true))
        }
      else
        sender() ! ReviewDeletedResponse(notFoundExceptionCreator(id))

    case CreateReviewFromCSV(review) =>
      val steamReviewId = reviewManagerState.reviewCount
      if (!reviewManagerState.reviews.contains(steamReviewId)) {
        val reviewActor      = context.actorOf(
          ReviewActor.props(steamReviewId),
          createActorName(steamReviewId)
        )
        val controlledReview = ReviewController(reviewActor)

        persist(ReviewActorCreated(steamReviewId)) { _ =>
          reviewManagerState = reviewManagerState.copy(
            reviewManagerState.reviewCount + 1,
            reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
          )

          tryToSaveSnapshot()

          reviewActor ! CreateReview(
            steamAppId = review.steamAppId,
            authorId = review.authorId,
            region = review.region,
            timestampCreated = review.timestampCreated.getOrElse(longToBigInt(System.currentTimeMillis())),
            timestampUpdated = review.timestampUpdated.getOrElse(System.currentTimeMillis()),
            review = review.review,
            recommended = review.recommended,
            votesHelpful = review.votesHelpful,
            votesFunny = review.votesFunny,
            weightedVoteScore = review.weightedVoteScore,
            commentCount = review.commentCount,
            steamPurchase = review.steamPurchase,
            receivedForFree = review.receivedForFree,
            writtenDuringEarlyAccess = review.writtenDuringEarlyAccess,
            authorPlaytimeForever = review.authorPlaytimeForever,
            authorPlaytimeLastTwoWeeks = review.authorPlaytimeLastTwoWeeks,
            authorPlaytimeAtReview = review.authorPlaytimeAtReview,
            authorLastPlayed = review.authorLastPlayed
          )
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
    //      log.info(s"Got unhandled message: $any")

  }

  override def receiveRecover: Receive = {
    case ReviewActorCreated(steamReviewId) =>
      val reviewActorName = createActorName(steamReviewId)
      val reviewActor     = context.child(reviewActorName)
        .getOrElse(
          context.actorOf(
            ReviewActor.props(steamReviewId),
            reviewActorName
          )
        )

      val controlledReview = ReviewController(reviewActor)

      reviewManagerState = reviewManagerState.copy(
        reviewCount = steamReviewId + 1,
        reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
      )

    case ReviewActorDeleted(id) =>
      reviewManagerState.reviews(id).isDisabled = true

    case SnapshotOffer(metadata, state: ReviewManager) =>
      log.info(s"Recovered snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
      reviewManagerState = state

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")

  }
}
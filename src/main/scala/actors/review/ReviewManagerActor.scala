package dev.galre.josue.akkaProject
package actors.review

import actors.ReviewController
import actors.review.ReviewActor.ReviewState
import util.CborSerializable

import akka.actor.{ ActorLogging, Props }
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }


object ReviewManagerActor {

  // reviews
  case class ReviewManager(
    var reviewCount: Long = 0,
    reviews:         mutable.HashMap[Long, ReviewController]
  ) extends CborSerializable

  val reviewManagerSnapshotInterval = 1000

  // commands
  case class CreateReviewFromCSV(review: ReviewState)

  case class GetAllReviewsByAuthor(authorId: Long, page: Int, perPage: Int)

  case class GetAllReviewsByGame(steamAppId: Long, page: Int, perPage: Int)

  // events
  case class ReviewActorCreated(
    @JsonDeserialize(contentAs = classOf[Long]) id:         Long,
    @JsonDeserialize(contentAs = classOf[Long]) authorId:   Long,
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId: Long
  ) extends CborSerializable

  case class ReviewActorDeleted(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long
  ) extends CborSerializable

  // responses
  case class ReviewsByFilterContent(perPage: Int, reviews: List[Option[ReviewState]])

  type GetAllReviewsByFilterResponse = Either[String, ReviewsByFilterContent]

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new ReviewManagerActor())
}

class ReviewManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import ReviewActor._
  import ReviewManagerActor._

  var reviewManagerState: ReviewManager = ReviewManager(reviews = mutable.HashMap())

  override def persistenceId: String = "steam-review-manager"

  def isReviewAvailable(id: Long): Boolean =
    reviewManagerState.reviews.contains(id) && !reviewManagerState.reviews(id).isDisabled

  def createActorName(steamReviewId: Long): String = s"steam-review-$steamReviewId"

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"A review with the id $id couldn't be found")

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % reviewManagerSnapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(reviewManagerState)

  def getReviewInfoResponseByFilter(filteredReviews: Iterable[ReviewController]): Future[Iterable[Option[ReviewState]]] = {
    Future.traverse(
      filteredReviews
    ) { reviewController =>
      (reviewController.actor ? GetReviewInfo(0)).mapTo[GetReviewInfoResponse].map {
        case Right(review) => Some(review)

        case Left(_) => None
      }

    }
  }

  def filterRecursive(condition: ReviewController => Boolean, page: Int, perPage: Int): List[ReviewController] = {
    @tailrec
    def filterHelper(acc: List[ReviewController], start: Long): List[ReviewController] = {
      if (acc.size == perPage || start > reviewManagerState.reviews.size) acc
      else {
        val newAcc = reviewManagerState.reviews.get(start) match {
          case Some(review) if condition(review) && !review.isDisabled => acc :+ review
          case _ => acc
        }

        filterHelper(newAcc, start + 1)
      }
    }

    filterHelper(Nil, page)
  }

  override def receiveCommand: Receive = {
    case CreateReview(review) =>
      val steamReviewId    = reviewManagerState.reviewCount
      val reviewActorName  = createActorName(steamReviewId)
      val reviewActor      = context.actorOf(
        ReviewActor.props(steamReviewId),
        reviewActorName
      )
      val controlledReview = ReviewController(reviewActor, review.authorId, review.steamAppId)

      persist(ReviewActorCreated(steamReviewId, review.authorId, review.steamAppId)) { _ =>
        reviewManagerState = reviewManagerState.copy(
          reviewCount = reviewManagerState.reviewCount + 1,
          reviews = reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
        )

        //        tryToSaveSnapshot()

        reviewActor.forward(CreateReview(review.copy(reviewId = steamReviewId)))
      }

    case getCommand @ GetReviewInfo(id) =>
      if (isReviewAvailable(id))
        reviewManagerState.reviews(id).actor.forward(getCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case GetAllReviewsByAuthor(authorId, page, perPage) =>
      val filteredRecursiveReviews = filterRecursive(_.userId == authorId, page, perPage)
      val replyTo                  = sender()

      val paginatedReviews = getReviewInfoResponseByFilter(filteredRecursiveReviews)

      paginatedReviews.onComplete {
        case Success(value) =>
          replyTo ! Right(ReviewsByFilterContent(perPage, value.toList))

        case Failure(exception) =>
          exception.printStackTrace()
          replyTo ! Left("There was a failure while trying to extract all the reviews from this user, please try again later.")
      }

    case GetAllReviewsByGame(steamAppId, page, perPage) =>
      val filteredRecursiveReviews = filterRecursive(_.steamAppId == steamAppId, page, perPage)
      val replyTo                  = sender()

      val paginatedReviews = getReviewInfoResponseByFilter(filteredRecursiveReviews)

      paginatedReviews.onComplete {
        case Success(value) =>
          replyTo ! Right(ReviewsByFilterContent(perPage, value.toList))

        case Failure(exception) =>
          exception.printStackTrace()
          replyTo ! Left("There was a failure while trying to extract all the reviews of this game, please try again later.")

      }

    case updateCommand @ UpdateReview(review) =>
      if (isReviewAvailable(review.reviewId))
        reviewManagerState.reviews(review.reviewId).actor.forward(updateCommand)
      else
        sender() ! notFoundExceptionCreator(review.reviewId)

    case DeleteReview(id) =>
      if (isReviewAvailable(id))
        persist(ReviewActorDeleted(id)) { _ =>
          reviewManagerState.reviews(id).isDisabled = true
          context.stop(reviewManagerState.reviews(id).actor)

          //          tryToSaveSnapshot()

          sender() ! Right(true)
        }
      else
        sender() ! notFoundExceptionCreator(id)

    case CreateReviewFromCSV(review) =>
      val steamReviewId = reviewManagerState.reviewCount
      if (!reviewManagerState.reviews.contains(steamReviewId)) {
        val reviewActor      = context.actorOf(
          ReviewActor.props(steamReviewId),
          createActorName(steamReviewId)
        )
        val controlledReview = ReviewController(reviewActor, review.authorId, review.steamAppId)

        persist(ReviewActorCreated(steamReviewId, review.authorId, review.steamAppId)) { _ =>
          log.info("Review created {} with data {}", steamReviewId, review)
          reviewManagerState = reviewManagerState.copy(
            reviewManagerState.reviewCount + 1,
            reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
          )

          //          tryToSaveSnapshot()

          reviewActor ! CreateReview(review.copy(reviewId = steamReviewId))
        }
      }
    //
    //    case SaveSnapshotSuccess(metadata) =>
    //      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    //
    //    case SaveSnapshotFailure(metadata, reason) =>
    //      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
      log.info(s"Got unhandled message: $any")

  }

  override def receiveRecover: Receive = {
    case ReviewActorCreated(steamReviewId, authorId, steamAppId) =>
      val reviewActorName = createActorName(steamReviewId)
      val reviewActor     = context.child(reviewActorName)
        .getOrElse(
          context.actorOf(
            ReviewActor.props(steamReviewId),
            reviewActorName
          )
        )

      val controlledReview = ReviewController(reviewActor, authorId, steamAppId)

      reviewManagerState = reviewManagerState.copy(
        reviewCount = steamReviewId + 1,
        reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
      )

    case ReviewActorDeleted(id) =>
      reviewManagerState.reviews(id).isDisabled = true

    //    case SnapshotOffer(metadata, state: ReviewManager) =>
    //      log.info(s"Recovered snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
    //      log.info(s"Got snapshot with state: $state")
    //      reviewManagerState = state

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")

  }
}
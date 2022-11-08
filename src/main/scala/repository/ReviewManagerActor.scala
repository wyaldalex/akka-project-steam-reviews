package dev.galre.josue.steamreviews
package repository

import repository.entity.ReviewActor
import repository.entity.ReviewActor._
import service.utils.{ Serializable, SnapshotSerializable }

import ReviewManagerActor._
import akka.actor.{ ActorLogging, Props }
import akka.pattern.{ ask, pipe }
import akka.persistence._
import akka.util.Timeout
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

object ReviewManagerActor {

  // reviews
  final case class ReviewManager(
    var reviewCount: Long = 0,
    reviews: mutable.HashMap[Long, ReviewController]
  )
    extends Serializable

  val ReviewManagerSnapshotInterval = 1000

  // commands
  final case class CreateReviewFromCSV(review: ReviewState)

  final case class GetAllReviewsByAuthor(authorId: Long, page: Int, perPage: Int)

  final case class GetAllReviewsByGame(steamAppId: Long, page: Int, perPage: Int)

  // events
  final case class ReviewActorCreated(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long,
    @JsonDeserialize(contentAs = classOf[Long]) authorId: Long,
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId: Long
  ) extends Serializable

  final case class ReviewActorDeleted(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long
  ) extends Serializable

  // responses
  final case class ReviewsByFilterContent(
    perPage: Int,
    reviews: List[Option[ReviewState]]
  )

  type GetAllReviewsByFilterResponse = Either[String, ReviewsByFilterContent]

  // snapshot
  final case class ReviewSnapshot(
    @JsonDeserialize(contentAs = classOf[Long]) reviewId: Long,
    @JsonDeserialize(contentAs = classOf[Long]) authorId: Long,
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId: Long
  ) extends SnapshotSerializable

  final case class ReviewManagerSnapshotSave(
    @JsonDeserialize(
      contentAs = classOf[Long]
    ) reviewCount: Long,
    reviewTupleList: List[ReviewSnapshot]
  )
    extends SnapshotSerializable

  def props(
    implicit timeout: Timeout,
    executionContext: ExecutionContext
  ): Props =
    Props(new ReviewManagerActor())
}

class ReviewManagerActor(
  implicit timeout: Timeout,
  executionContext: ExecutionContext
)
  extends PersistentActor
  with ActorLogging {

  var reviewManagerState: ReviewManager = ReviewManager(
    reviews = mutable.HashMap()
  )

  override def persistenceId: String = "steam-review-manager"

  def isReviewAvailable(id: Long): Boolean =
    reviewManagerState.reviews.contains(id) && !reviewManagerState
      .reviews(id)
      .isDisabled

  def createActorName(steamReviewId: Long): String =
    "steam-review-" + steamReviewId

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"A review with the id $id couldn't be found")

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % ReviewManagerSnapshotInterval == 0 && lastSequenceNr != 0) {
      val currentList = reviewManagerState.reviews
      val reviewList = currentList
        .map(
          review =>
            ReviewSnapshot(review._1, review._2.userId, review._2.steamAppId)
        )
        .toList
      val snapshotToSave =
        ReviewManagerSnapshotSave(reviewManagerState.reviewCount, reviewList)
      log.info(
        s"Creating snapshot with ${reviewList.size} entries on ReviewManagerActor"
      )

      saveSnapshot(snapshotToSave)
    }

  def getReviewInfoResponseByFilter(
    filteredReviews: Iterable[ReviewController]
  ): Future[Iterable[Option[ReviewState]]] = {
    Future.traverse(filteredReviews) {
      reviewController =>
        (reviewController.actor ? GetReviewInfo(reviewController.userId))
          .mapTo[GetReviewInfoResponse]
          .map {
            case Right(review) => Some(review)

            case Left(_) => None
          }

    }
  }

  def filterRecursive(
    condition: ReviewController => Boolean,
    page: Int,
    perPage: Int
  ): List[ReviewController] = {
    @tailrec
    def filterHelper(
      acc: Vector[ReviewController],
      start: Long
    ): List[ReviewController] = {
      if (acc.size == perPage || start > reviewManagerState.reviews.size) acc.toList
      else {
        val newAcc = reviewManagerState.reviews.get(start) match {
          case Some(review) if condition(review) && !review.isDisabled =>
            acc :+ review
          case _ => acc
        }

        filterHelper(newAcc, start + 1)
      }
    }

    filterHelper(Vector.empty, page)
  }

  override def receiveCommand: Receive = {
    case CreateReview(review) =>
      val steamReviewId = reviewManagerState.reviewCount
      val reviewActorName = createActorName(steamReviewId)
      val reviewActor =
        context.actorOf(ReviewActor.props(steamReviewId), reviewActorName)
      val controlledReview =
        ReviewController(reviewActor, review.authorId, review.steamAppId)

      persist(
        ReviewActorCreated(steamReviewId, review.authorId, review.steamAppId)
      ) {
        _ =>
          reviewManagerState = reviewManagerState.copy(
            reviewCount = reviewManagerState.reviewCount + 1,
            reviews =
              reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
          )

          tryToSaveSnapshot()

          reviewActor.forward(CreateReview(review.copy(reviewId = steamReviewId)))
      }

    case getCommand @ GetReviewInfo(id) =>
      if (isReviewAvailable(id)) {
        reviewManagerState.reviews(id).actor.forward(getCommand)
      } else {
        sender() ! notFoundExceptionCreator(id)
      }
      
    case GetAllReviewsByAuthor(authorId, page, perPage) =>
      val filteredRecursiveReviews =
        filterRecursive(_.userId == authorId, page, perPage)

      val paginatedReviews = getReviewInfoResponseByFilter(
        filteredRecursiveReviews
      )

      paginatedReviews.pipeTo(sender())

    case GetAllReviewsByGame(steamAppId, page, perPage) =>
      val filteredRecursiveReviews =
        filterRecursive(_.steamAppId == steamAppId, page, perPage)

      val paginatedReviews = getReviewInfoResponseByFilter(
        filteredRecursiveReviews
      )

      paginatedReviews.pipeTo(sender())

    case updateCommand @ UpdateReview(review) =>
      if (isReviewAvailable(review.reviewId)) {
        reviewManagerState.reviews(review.reviewId).actor.forward(updateCommand)
      } else {
        sender() ! notFoundExceptionCreator(review.reviewId)
      }

    case DeleteReview(id) =>
      if (isReviewAvailable(id)) {
        persist(ReviewActorDeleted(id)) {
          _ =>
            reviewManagerState.reviews(id).isDisabled = true
            context.stop(reviewManagerState.reviews(id).actor)

            tryToSaveSnapshot()

            sender() ! Right(value = true)
        }
      } else {
        sender() ! notFoundExceptionCreator(id)
      }

    case CreateReviewFromCSV(review) =>
      val steamReviewId = reviewManagerState.reviewCount
      if (!reviewManagerState.reviews.contains(steamReviewId)) {
        val reviewActor = context.actorOf(
          ReviewActor.props(steamReviewId),
          createActorName(steamReviewId)
        )
        val controlledReview =
          ReviewController(reviewActor, review.authorId, review.steamAppId)

        persist(
          ReviewActorCreated(steamReviewId, review.authorId, review.steamAppId)
        ) {
          _ =>
            reviewManagerState = reviewManagerState.copy(
              reviewManagerState.reviewCount + 1,
              reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
            )

            tryToSaveSnapshot()

            reviewActor ! CreateReview(review.copy(reviewId = steamReviewId))
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(getSavedSnapshotMessage("ReviewManagerActor", metadata))

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(getFailedSnapshotMessage("ReviewManagerActor", metadata, reason))
      reason.printStackTrace()

    case any: Any =>

  }

  def createReviewControllerFromRecover(
    steamReviewId: Long,
    authorId: Long,
    steamAppId: Long
  ): ReviewController = {
    val reviewActorName = createActorName(steamReviewId)
    val reviewActor = context
      .child(reviewActorName)
      .getOrElse(
        context.actorOf(ReviewActor.props(steamReviewId), reviewActorName)
      )

    ReviewController(reviewActor, authorId, steamAppId)
  }

  override def receiveRecover: Receive = {
    case ReviewActorCreated(steamReviewId, authorId, steamAppId) =>
      val controlledReview =
        createReviewControllerFromRecover(steamReviewId, authorId, steamAppId)

      reviewManagerState = reviewManagerState.copy(
        reviewCount = steamReviewId + 1,
        reviewManagerState.reviews.addOne(steamReviewId -> controlledReview)
      )

    case ReviewActorDeleted(id) =>
      reviewManagerState.reviews(id).isDisabled = true

    case SnapshotOffer(
    metadata,
    ReviewManagerSnapshotSave(reviewCount, reviewTupleList)
    ) =>
      log.info(
        s"Recovered review snapshot ${metadata.persistenceId} - ${metadata.timestamp}"
      )
      reviewManagerState = reviewManagerState.copy(reviewCount = reviewCount)

      reviewTupleList.foreach {
        case ReviewSnapshot(steamReviewId, authorId, steamAppId) =>
          val controlledUser = createReviewControllerFromRecover(
            steamReviewId,
            authorId,
            steamAppId
          )

          reviewManagerState = reviewManagerState.copy(
            reviews =
              reviewManagerState.reviews.addOne(steamReviewId -> controlledUser)
          )
      }

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")

  }
}

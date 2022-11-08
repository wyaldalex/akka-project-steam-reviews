package dev.galre.josue.steamreviews
package repository.entity

import service.utils.Serializable

import ReviewActor._
import akka.actor.{ ActorLogging, Props }
import akka.persistence.PersistentActor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

object ReviewActor {
  // state
  final case class ReviewState(
    @JsonDeserialize(contentAs = classOf[Long]) reviewId: Long,
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId: Long = 0,
    @JsonDeserialize(contentAs = classOf[Long]) authorId: Long = 0,
    region: Option[String] = None,
    review: Option[String] = None,
    @JsonDeserialize(contentAs = classOf[Long]) timestampCreated: Option[Long] = None,
    @JsonDeserialize(contentAs = classOf[Long]) timestampUpdated: Option[Long] = None,
    recommended: Option[Boolean] = None,
    @JsonDeserialize(contentAs = classOf[Long]) votesHelpful: Option[Long] = None,
    @JsonDeserialize(contentAs = classOf[Long]) votesFunny: Option[Long] = None,
    weightedVoteScore: Option[Double] = None,
    @JsonDeserialize(contentAs = classOf[Long]) commentCount: Option[Long] = None,
    steamPurchase: Option[Boolean] = None,
    receivedForFree: Option[Boolean] = None,
    writtenDuringEarlyAccess: Option[Boolean] = None,
    authorPlaytimeForever: Option[Double] = None,
    authorPlaytimeLastTwoWeeks: Option[Double] = None,
    authorPlaytimeAtReview: Option[Double] = None,
    authorLastPlayed: Option[Double] = None,
  ) extends Serializable

  // commands
  final case class CreateReview(review: ReviewState)

  final case class UpdateReview(review: ReviewState)

  final case class DeleteReview(reviewId: Long)

  final case class GetReviewInfo(reviewId: Long)


  // events
  final case class ReviewCreated(review: ReviewState) extends Serializable

  final case class ReviewUpdated(review: ReviewState) extends Serializable


  // responses
  type ReviewCreatedResponse = Either[String, ReviewState]

  type ReviewUpdatedResponse = Either[String, ReviewState]

  type GetReviewInfoResponse = Either[String, ReviewState]

  type ReviewDeletedResponse = Either[String, Boolean]

  def props(reviewId: Long): Props = Props(new ReviewActor(reviewId))

}

class ReviewActor(reviewId: Long)
  extends PersistentActor
  with ActorLogging {

  var state: ReviewState = ReviewState(reviewId)

  def applyUpdate(newState: ReviewState): ReviewState = {
    // no need to update this information
    // reviewId is unique
    // the review cannot be changed to another game or author
    // the timestamp of creation is immutable, created one
    // the timestampUpdated is automatically when an update is created
    val newTimestampUpdated = Some(System.currentTimeMillis())
    // steamPurchase or receivedForFree are immutable since
    // both indicate if the user owns the game and how they acquired it
    // if the review was created during early access ???
    // should be immutable since the review won't be changed

    // everything else should be changed
    val newReceivedForFree =
      if (newState.receivedForFree.isEmpty) {
        state.receivedForFree
      } else {
        newState.receivedForFree
      }

    val newRegion =
      if (newState.region.isEmpty) {
        state.region
      } else {
        newState.region
      }

    val newReview =
      if (newState.review.isEmpty) {
        state.review
      } else {
        newState.review
      }

    val newRecommended =
      if (newState.recommended.isEmpty) {
        state.recommended
      } else {
        newState.recommended
      }

    val newVotesHelpful =
      if (newState.votesHelpful.isEmpty) {
        state.votesHelpful
      } else {
        newState.votesHelpful
      }

    val newVotesFunny =
      if (newState.votesFunny.isEmpty) {
        state.votesFunny
      } else {
        newState.votesFunny
      }

    val newWeightedVoteScore =
      if (newState.weightedVoteScore.isEmpty) {
        state.weightedVoteScore
      } else {
        newState.weightedVoteScore
      }

    val newCommentCount =
      if (newState.commentCount.isEmpty) {
        state.commentCount
      } else {
        newState.commentCount
      }

    val newAuthorPlaytimeForever =
      if (newState.authorPlaytimeForever.isEmpty) {
        state.authorPlaytimeForever
      } else {
        newState.authorPlaytimeForever
      }

    val newAuthorPlaytimeLastTwoWeeks =
      if (newState.authorPlaytimeLastTwoWeeks.isEmpty) {
        state.authorPlaytimeLastTwoWeeks
      } else {
        newState.authorPlaytimeLastTwoWeeks
      }

    val newAuthorPlaytimeAtReview =
      if (newState.authorPlaytimeAtReview.isEmpty) {
        state.authorPlaytimeAtReview
      } else {
        newState.authorPlaytimeAtReview
      }

    val newAuthorLastPlayed =
      if (newState.authorLastPlayed.isEmpty) {
        state.authorLastPlayed
      } else {
        newState.authorLastPlayed
      }

    state.copy(
      region = newRegion,
      review = newReview,
      timestampUpdated = newTimestampUpdated,
      recommended = newRecommended,
      votesHelpful = newVotesHelpful,
      votesFunny = newVotesFunny,
      weightedVoteScore = newWeightedVoteScore,
      commentCount = newCommentCount,
      receivedForFree = newReceivedForFree,
      authorPlaytimeForever = newAuthorPlaytimeForever,
      authorPlaytimeLastTwoWeeks = newAuthorPlaytimeLastTwoWeeks,
      authorPlaytimeAtReview = newAuthorPlaytimeAtReview,
      authorLastPlayed = newAuthorLastPlayed
    )
  }

  override def persistenceId: String = s"steam-review-$reviewId"

  override def receiveCommand: Receive = {
    case CreateReview(review) =>
      val id = state.reviewId
      val timestampCreated = Some(System.currentTimeMillis())
      val timestampUpdated = timestampCreated

      if (review.review.isEmpty) {
        sender() ! Left("You need to provide a valid review.")
      } else {
        persist(
          ReviewCreated(
            review.copy(
              reviewId = id,
              timestampCreated = timestampCreated,
              timestampUpdated = timestampUpdated
            )
          )
        ) {
          event =>
            state = event.review

            sender() ! Right(state)
        }
      }

    case UpdateReview(review) =>
      persist(ReviewUpdated(applyUpdate(review))) {
        event =>
          val newState = event.review
          state = newState

          sender() ! Right(newState)
      }

    case GetReviewInfo(_) =>
      sender() ! Right(state)
  }

  override def receiveRecover: Receive = {
    case ReviewCreated(review) =>
      state = review

    case ReviewUpdated(review) =>
      state = applyUpdate(review)

  }
}

package dev.galre.josue.akkaProject
package actors.review

import util.CborSerializable

import akka.actor.Props
import akka.persistence.PersistentActor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.util.{ Success, Try }

object ReviewActor {
  // state
  case class ReviewState(
    @JsonDeserialize(contentAs = classOf[Long]) reviewId:                 Long,
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId:               Long = 0,
    @JsonDeserialize(contentAs = classOf[Long]) authorId:                 Long = 0,
    region:                                                               Option[String] = None,
    review:                                                               Option[String] = None,
    @JsonDeserialize(contentAs = classOf[Option[Long]]) timestampCreated: Option[Long] = None,
    @JsonDeserialize(contentAs = classOf[Option[Long]]) timestampUpdated: Option[Long] = None,
    recommended:                                                          Option[Boolean] = None,
    @JsonDeserialize(contentAs = classOf[Option[Long]]) votesHelpful:     Option[Long] = None,
    @JsonDeserialize(contentAs = classOf[Option[Long]]) votesFunny:       Option[Long] = None,
    weightedVoteScore:                                                    Option[Double] = None,
    @JsonDeserialize(contentAs = classOf[Option[Long]]) commentCount:     Option[Long] = None,
    steamPurchase:                                                        Option[Boolean] = None,
    receivedForFree:                                                      Option[Boolean] = None,
    writtenDuringEarlyAccess:                                             Option[Boolean] = None,
    authorPlaytimeForever:                                                Option[Double] = None,
    authorPlaytimeLastTwoWeeks:                                           Option[Double] = None,
    authorPlaytimeAtReview:                                               Option[Double] = None,
    authorLastPlayed:                                                     Option[Double] = None,
  ) extends CborSerializable

  // commands
  case class CreateReview(
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId:           Long,
    @JsonDeserialize(contentAs = classOf[Long]) authorId:             Long,
    region:                                                           Option[String],
    timestampCreated:                                                 Long = System.currentTimeMillis(),
    timestampUpdated:                                                 Long = System.currentTimeMillis(),
    review:                                                           Option[String],
    recommended:                                                      Option[Boolean],
    @JsonDeserialize(contentAs = classOf[Option[Long]]) votesHelpful: Option[Long],
    @JsonDeserialize(contentAs = classOf[Option[Long]]) votesFunny:   Option[Long],
    weightedVoteScore:                                                Option[Double],
    @JsonDeserialize(contentAs = classOf[Option[Long]]) commentCount: Option[Long],
    steamPurchase:                                                    Option[Boolean],
    receivedForFree:                                                  Option[Boolean],
    writtenDuringEarlyAccess:                                         Option[Boolean],
    authorPlaytimeForever:                                            Option[Double],
    authorPlaytimeLastTwoWeeks:                                       Option[Double],
    authorPlaytimeAtReview:                                           Option[Double],
    authorLastPlayed:                                                 Option[Double]
  )

  case class UpdateReview(review: ReviewState)

  case class DeleteReview(reviewId: Long)

  case class GetReviewInfo(reviewId: Long)


  // events
  case class ReviewCreated(review: ReviewState) extends CborSerializable

  case class ReviewUpdated(review: ReviewState) extends CborSerializable


  // responses
  case class ReviewCreatedResponse(reviewId: Try[Long])

  case class ReviewUpdatedResponse(maybeAccount: Try[ReviewState])

  case class GetReviewInfoResponse(maybeAccount: Try[ReviewState])

  case class ReviewDeletedResponse(accountWasDeletedSuccessfully: Try[Boolean])

  def props(reviewId: Long): Props = Props(new ReviewActor(reviewId))

}

class ReviewActor(reviewId: Long) extends PersistentActor {

  import ReviewActor._

  var state: ReviewState = ReviewState(reviewId)

  def applyUpdate(newState: ReviewState): ReviewState = {
    // no need to update this information
    // reviewId is unique
    val reviewId                    = state.reviewId
    // the review cannot be changed to another game or author
    val steamAppId                  = state.steamAppId
    val authorId                    = state.authorId
    // the timestamp of creation is immutable, created one
    val timestampCreated            = state.timestampCreated
    // the timestampUpdated is automatically when an update is created
    val newTimestampUpdated         = Some(System.currentTimeMillis())
    // steamPurchase or receivedForFree are immutable since
    // both indicate if the user owns the game and how they acquired it
    val newSteamPurchase            = state.steamPurchase
    // if the review was created during early access ???
    // should be immutable since the review won't be changed
    val newWrittenDuringEarlyAccess = state.writtenDuringEarlyAccess

    // everything else should be changed
    val newReceivedForFree =
      if (newState.receivedForFree.isEmpty)
        state.receivedForFree
      else
        newState.receivedForFree

    val newRegion =
      if (newState.region.isEmpty)
        state.region
      else
        newState.region

    val newReview =
      if (newState.review.isEmpty)
        state.review
      else
        newState.review

    val newRecommended =
      if (newState.recommended.isEmpty)
        state.recommended
      else
        newState.recommended

    val newVotesHelpful =
      if (newState.votesHelpful.isEmpty)
        state.votesHelpful
      else
        newState.votesHelpful

    val newVotesFunny =
      if (newState.votesFunny.isEmpty)
        state.votesFunny
      else
        newState.votesFunny

    val newWeightedVoteScore =
      if (newState.weightedVoteScore.isEmpty)
        state.weightedVoteScore
      else
        newState.weightedVoteScore

    val newCommentCount =
      if (newState.commentCount.isEmpty)
        state.commentCount
      else
        newState.commentCount

    val newAuthorPlaytimeForever =
      if (newState.authorPlaytimeForever.isEmpty)
        state.authorPlaytimeForever
      else
        newState.authorPlaytimeForever

    val newAuthorPlaytimeLastTwoWeeks =
      if (newState.authorPlaytimeLastTwoWeeks.isEmpty)
        state.authorPlaytimeLastTwoWeeks
      else
        newState.authorPlaytimeLastTwoWeeks

    val newAuthorPlaytimeAtReview =
      if (newState.authorPlaytimeAtReview.isEmpty)
        state.authorPlaytimeAtReview
      else
        newState.authorPlaytimeAtReview

    val newAuthorLastPlayed =
      if (newState.authorLastPlayed.isEmpty)
        state.authorLastPlayed
      else
        newState.authorLastPlayed

    ReviewState(
      reviewId = reviewId,
      steamAppId = steamAppId,
      authorId = authorId,
      region = newRegion,
      review = newReview,
      timestampCreated = timestampCreated,
      timestampUpdated = newTimestampUpdated,
      recommended = newRecommended,
      votesHelpful = newVotesHelpful,
      votesFunny = newVotesFunny,
      weightedVoteScore = newWeightedVoteScore,
      commentCount = newCommentCount,
      steamPurchase = newSteamPurchase,
      receivedForFree = newReceivedForFree,
      writtenDuringEarlyAccess = newWrittenDuringEarlyAccess,
      authorPlaytimeForever = newAuthorPlaytimeForever,
      authorPlaytimeLastTwoWeeks = newAuthorPlaytimeLastTwoWeeks,
      authorPlaytimeAtReview = newAuthorPlaytimeAtReview,
      authorLastPlayed = newAuthorLastPlayed
    )
  }

  override def persistenceId: String = s"steam-review-$reviewId"

  override def receiveCommand: Receive = {
    case CreateReview(
    steamAppId,
    authorId,
    region,
    _,
    _,
    review,
    recommended,
    votesHelpful,
    votesFunny,
    weightedVoteScore,
    commentCount,
    steamPurchase,
    receivedForFree,
    writtenDuringEarlyAccess,
    authorPlaytimeLastTwoWeeks,
    authorPlaytimeForever,
    authorPlaytimeAtReview,
    authorLastPlayed
    ) =>
      val id               = state.reviewId
      val timestampCreated = Some(System.currentTimeMillis())
      val timestampUpdated = timestampCreated

      if (review.isEmpty)
        sender() ! new IllegalArgumentException("You need to provide a valid review.")

      persist(
        ReviewCreated(
          ReviewState(
            id,
            steamAppId,
            authorId,
            region,
            review,
            timestampCreated,
            timestampUpdated,
            recommended,
            votesHelpful,
            votesFunny,
            weightedVoteScore,
            commentCount,
            steamPurchase,
            receivedForFree,
            writtenDuringEarlyAccess,
            authorPlaytimeLastTwoWeeks,
            authorPlaytimeForever,
            authorPlaytimeAtReview,
            authorLastPlayed
          )
        )
      ) { event =>
        state = event.review

        sender() ! ReviewCreatedResponse(Success(id))
      }

    case UpdateReview(review) =>
      persist(ReviewUpdated(applyUpdate(review))) { event =>
        val newState = event.review
        state = newState

        sender() ! ReviewUpdatedResponse(Success(newState))
      }

    case GetReviewInfo(_) =>
      sender() ! GetReviewInfoResponse(Success(state))
  }

  override def receiveRecover: Receive = {
    case ReviewCreated(review) =>
      state = review

    case ReviewUpdated(review) =>
      state = applyUpdate(review)

  }
}

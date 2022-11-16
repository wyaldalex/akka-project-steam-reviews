package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.{ CreateReview, GetReviewInfo, ReviewState, UpdateReview }
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.util.Random

class ReviewActorTestGherkin extends GherkinSpec {

  Feature("A ReviewActor behavior ") {
    val reviewId: Long = Math.abs(Random.nextLong())
    val reviewState: ReviewState = ReviewState(
      reviewId, 13131L, 13132L, Some("US/EAST"),
      Some("Good Game"), Some(1668458476L), Some(1668458476L), Some(true), Some(100L), Some(100L),
      Some(78.3), Some(10), Some(true), Some(false), Some(false),
      Some(1), Some(1), Some(1), Some(1))
    val reviewActor: ActorRef = system.actorOf(ReviewActor.props(reviewId))

    Scenario("Return a ReviewState when receiving a valid CreateReview command") {
      Given("A ReviewActor and a valid ReviewState")

      When("CreateReview command sent")
      reviewActor ! CreateReview(reviewState)

      Then("ReviewState should be returned with only different timestamps")
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)
      val originalStateExceptTimestamps = reviewState.copy(
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)

    }
    
    Scenario("Return a Left(String) when receiving an invalid CreateReview command") {
      Given("A ReviewActor and an invalid ReviewState")

      When("invalid CreateReview command sent")
      reviewActor ! CreateReview(reviewState.copy(review = Option.empty))

      Then("It should return a String with error message")
      val result = expectMsgClass(classOf[Left[String, ReviewState]])
      assert(result.isLeft)
    }
    
    Scenario("Return a Right(ReviewState) when receiving a valid UpdateReview command") {

      Given("A ReviewActor and a ReviewState for update")
      val updatedReceiveForFree = Some(true)
      val updatedRegion = Some("EAST/ASIA")
      val updatedReview = Some("Bad Game")
      val updatedRecommended = Some(false)
      val updatedVotesHelpful = Some(111L)
      val updatedVotesFunny = Some(111L)
      val updatedWeightedVoteScore = Some(33.33)
      val updatedCommentCount = Some(111L)
      val updatedAuthorPlaytimeForever = Some(2.0)
      val updatedAuthorPlaytimeLastTwoWeeks = Some(2.0)
      val updatedAuthorPlaytimeAtReview = Some(2.0)
      val updatedNewAuthorLastPlayed = Some(2.0)

      reviewActor ! CreateReview(reviewState)
      expectMsgClass(classOf[Right[String, ReviewState]])

      When("UpdateReview sent")
      reviewActor ! UpdateReview(reviewState.copy(
        receivedForFree = updatedReceiveForFree, region = updatedRegion,
        review = updatedReview, recommended = updatedRecommended, votesHelpful = updatedVotesHelpful,
        votesFunny = updatedVotesFunny, weightedVoteScore = updatedWeightedVoteScore, commentCount = updatedCommentCount,
        authorPlaytimeForever = updatedAuthorPlaytimeForever, authorPlaytimeLastTwoWeeks = updatedAuthorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = updatedAuthorPlaytimeAtReview, authorLastPlayed = updatedNewAuthorLastPlayed))

      Then("The newly updated ReviewState should be returned")
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)

      val originalStateExceptTimestamps = reviewState.copy(
        receivedForFree = updatedReceiveForFree, region = updatedRegion,
        review = updatedReview, recommended = updatedRecommended, votesHelpful = updatedVotesHelpful,
        votesFunny = updatedVotesFunny, weightedVoteScore = updatedWeightedVoteScore, commentCount = updatedCommentCount,
        authorPlaytimeForever = updatedAuthorPlaytimeForever, authorPlaytimeLastTwoWeeks = updatedAuthorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = updatedAuthorPlaytimeAtReview, authorLastPlayed = updatedNewAuthorLastPlayed,
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)
      
    }

    Scenario("Return the ReviewState when receiving GetReviewInfo command") {
      Given("A ReviewActor with a ReviewState")
      reviewActor ! CreateReview(reviewState)
      expectMsgClass(classOf[Right[String, ReviewState]])

      When("GetReviewInfo command sent for the respective ReviewActor")
      reviewActor ! GetReviewInfo(reviewId)

      Then("The correct ReviewState should be returned")
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)
      val originalStateExceptTimestamps = reviewState.copy(
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)

    }

    Scenario("Handle empty options and return the original state on UpdateReview command") {
      Given("A ReviewActor with a ReviewState for update with empty options")
      reviewActor ! CreateReview(reviewState)
      expectMsgClass(classOf[Right[String, ReviewState]])

      When("UpdateReview command sent with empty options")
      reviewActor ! UpdateReview(reviewState.copy(
        receivedForFree = Option.empty, region = Option.empty,
        review = Option.empty, recommended = Option.empty, votesHelpful = Option.empty,
        votesFunny = Option.empty, weightedVoteScore = Option.empty, commentCount = Option.empty,
        authorPlaytimeForever = Option.empty, authorPlaytimeLastTwoWeeks = Option.empty,
        authorPlaytimeAtReview = Option.empty, authorLastPlayed = Option.empty))

      Then("Original ReviewState should be returned")
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)

      val originalStateExceptTimestamps = reviewState.copy(
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated)
      assert(originalStateExceptTimestamps == result.value)

    }

    Scenario("Recover ReviewState after a Restart") {

      Given("A ReviewActor with a ReviewState with history of changes")
      reviewActor ! CreateReview(reviewState)
      expectMsgClass(classOf[Right[String, ReviewState]])

      reviewActor ! UpdateReview(reviewState.copy(
        receivedForFree = Some(true)))
      val resultUpdate = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(resultUpdate.isRight)

      When("ReviewActor is restarted")
      reviewActor ! Kill

      Then("ReviewState should recover properly")
      val restartedReviewActor = system.actorOf(ReviewActor.props(reviewId))
      restartedReviewActor ! GetReviewInfo(reviewId)
      val recoverResult = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(recoverResult.isRight)

      val originalStateExceptTimestamps = reviewState.copy(
        timestampCreated = recoverResult.value.timestampCreated,
        timestampUpdated = recoverResult.value.timestampUpdated,
        receivedForFree = recoverResult.value.receivedForFree
      )
      assert(originalStateExceptTimestamps == recoverResult.value)

    }
    
  }
}

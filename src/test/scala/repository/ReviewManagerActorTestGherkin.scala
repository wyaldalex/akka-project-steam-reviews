package dev.galre.josue.steamreviews
package repository

import akka.actor.{ ActorRef, Kill }
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.ReviewActor._
import dev.galre.josue.steamreviews.repository.ReviewManagerActor.{ CreateReviewFromCSV, GetAllReviewsByAuthor, GetAllReviewsByGame }
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.duration._
import scala.util.Random

class ReviewManagerActorTestGherkin extends GherkinSpec {

  Feature("A ReviewManagerActor Behavior") {

    val reviewId: Long = Math.abs(Random.nextLong())
    val reviewState: ReviewState = ReviewState(
      reviewId, 13131L, 13132L, Some("US/EAST"),
      Some("Good Game"), Some(1668458476L), Some(1668458476L), Some(true), Some(100L), Some(100L),
      Some(78.3), Some(10), Some(true), Some(false), Some(false),
      Some(1), Some(1), Some(1), Some(1)
    )
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    implicit val timeout: Timeout = Timeout(20.seconds)
    val reviewManagerActor: ActorRef = system.actorOf(ReviewManagerActor.props(timeout, system.dispatcher))

    Scenario("Return a Right(ReviewState) on a valid CreateReview command") {

      Given("A ReviewManager Actor and a ReviewState")

      When("A CreateReview command is sent")
      reviewManagerActor ! CreateReview(reviewState)

      Then("A correct ReviewStat should be returned only with timestamps different")
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)
      val originalStateExceptTimestamps = reviewState.copy(
        reviewId = result.value.reviewId,
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)
    }

    Scenario("Return a Right(ReviewState) on a valid GetReviewInfo command") {

      Given("A ReviewManager Actor and a ReviewState")
      reviewManagerActor ! CreateReview(reviewState.copy(reviewId = reviewId))
      val createResult = expectMsgClass(classOf[Right[String, ReviewState]])

      When("GetReviewInfo command is sent")
      reviewManagerActor ! GetReviewInfo(createResult.value.reviewId)

      Then("Corresponding ReviewState is returned")
      val getResult = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(getResult.isRight)
      val originalStateExceptTimestamps = reviewState.copy(
        reviewId = createResult.value.reviewId,
        timestampCreated = getResult.value.timestampCreated,
        timestampUpdated = getResult.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == getResult.value)

    }

    Scenario("A ReviewManagerActor should return a List of ReviewStates for a given AuthorId") {
      Given("A ReviewManager Actor and a ReviewState with 2 reviews for the Same Author")

      val newAuthorId: Long = Math.abs(Random.nextLong())

      reviewManagerActor ! CreateReview(reviewState.copy(authorId = newAuthorId, steamAppId = Math.abs(Random.nextLong())))
      expectMsgClass(classOf[Right[String, ReviewState]])
      reviewManagerActor ! CreateReview(reviewState.copy(authorId = newAuthorId, steamAppId = Math.abs(Random.nextLong())))
      expectMsgClass(classOf[Right[String, ReviewState]])

      When("GetAllReviewsByAuthor is sent with an AuthorId")
      reviewManagerActor ! GetAllReviewsByAuthor(newAuthorId, page = 0, perPage = 2)

      Then("It should a List of 2 ReviewState")
      val listResponse = expectMsgClass(classOf[List[Some[ReviewState]]])
      assert(listResponse.size == 2)
    }

    Scenario("A ReviewManagerActor should return a List of ReviewStates for a given SteamAppId") {
      Given("A ReviewManager Actor and a ReviewState with 2 reviews for the Same SteamAppId")

      val newSteamAppId = Math.abs(Random.nextLong())

      reviewManagerActor ! CreateReview(reviewState.copy(steamAppId = newSteamAppId ))
      expectMsgClass(classOf[Right[String, ReviewState]])
      reviewManagerActor ! CreateReview(reviewState.copy( steamAppId = newSteamAppId ))
      expectMsgClass(classOf[Right[String, ReviewState]])

      When("GetAllReviewsByAuthor command is sent with a SteamAppId with 2 Reviews")
      reviewManagerActor ! GetAllReviewsByGame(newSteamAppId, page = 0, perPage = 2)

      Then("It should a List of 2 ReviewState")
      val listResponse = expectMsgClass(classOf[List[Some[ReviewState]]])
      assert(listResponse.size == 2)
    }

    Scenario("Return a Right(ReviewState) on a valid UpdateReview command") {

      Given("A ReviewManager Actor and a ReviewState")
      reviewManagerActor ! CreateReview(reviewState.copy(reviewId = reviewId))
      val createResult = expectMsgClass(classOf[Right[String, ReviewState]])

      When("UpdateReview command is sent")
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
      val updatedReviewState = reviewState.copy(
        reviewId = createResult.value.reviewId,
        receivedForFree = updatedReceiveForFree, region = updatedRegion,
        review = updatedReview, recommended = updatedRecommended, votesHelpful = updatedVotesHelpful,
        votesFunny = updatedVotesFunny, weightedVoteScore = updatedWeightedVoteScore, commentCount = updatedCommentCount,
        authorPlaytimeForever = updatedAuthorPlaytimeForever, authorPlaytimeLastTwoWeeks = updatedAuthorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = updatedAuthorPlaytimeAtReview, authorLastPlayed = updatedNewAuthorLastPlayed
      )
      reviewManagerActor ! UpdateReview(updatedReviewState)

      Then("Corresponding updated ReviewState is returned")
      val updateResult = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(updateResult.isRight)
      val expectedStateExceptTimestamps = updatedReviewState.copy(
        reviewId = createResult.value.reviewId,
        timestampCreated = updateResult.value.timestampCreated,
        timestampUpdated = updateResult.value.timestampUpdated
      )
      assert(expectedStateExceptTimestamps == updateResult.value)

    }

    Scenario("A ReviewManagerActor should return a true value when deleting an existing Review") {

      Given("A ReviewManager Actor and a ReviewState")
      reviewManagerActor ! CreateReview(reviewState.copy(reviewId = reviewId))
      val createResult = expectMsgClass(classOf[Right[String, ReviewState]])

      When("DeleteReview command is sent")
      reviewManagerActor ! DeleteReview(createResult.value.reviewId)

      Then("Corresponding true flag is returned")
      val deleteResponse = expectMsgClass(classOf[Right[String, Boolean]])
      assert(deleteResponse.value == true)
    }

    Scenario("Return a Right(ReviewState) on a valid CreateReviewFromCSV command") {

      Given("A ReviewManager Actor and a ReviewState")

      When("CreateReviewFromCSV command is sent")
      val newSteamAppId = Math.abs(Random.nextLong())
      reviewManagerActor ! CreateReviewFromCSV(reviewState.copy(steamAppId = newSteamAppId))

      Then("Corresponding ReviewState is returned")
      expectNoMessage()
      reviewManagerActor ! GetAllReviewsByGame(newSteamAppId, page = 0, perPage = 1)
      val listResponse = expectMsgClass(classOf[List[Some[ReviewState]]])
      assert(listResponse.size == 1)

      val autoGeneratedReviewId = listResponse(0).value.reviewId
      val autoGeneratedCreateTimeStamp = listResponse(0).value.timestampCreated
      val autoGeneratedUpdateTimeStamp = listResponse(0).value.timestampUpdated
      val expectedStateExceptTimestamps = listResponse(0).value.copy(
        reviewId = autoGeneratedReviewId,
        timestampCreated = autoGeneratedCreateTimeStamp,
        timestampUpdated = autoGeneratedUpdateTimeStamp
      )
      assert(expectedStateExceptTimestamps == listResponse(0).value)
    }

    Scenario("Return correct ReviewState after a Restart") {

      Given("A ReviewManager Actor and a ReviewState history")
      reviewManagerActor ! CreateReview(reviewState)
      val createResult = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(createResult.isRight)
      val autoGeneratedReviewId =  createResult.value.reviewId

      When("Actor is restarted")
      reviewManagerActor ! Kill
      val restartedReviewManagerActor: ActorRef = system.actorOf(ReviewManagerActor.props(timeout, system.dispatcher))

      Then("Corresponding ReviewState is returned")
      restartedReviewManagerActor ! GetReviewInfo(autoGeneratedReviewId)
      val getResult = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(getResult.isRight)
      val originalStateExceptTimestamps = reviewState.copy(
        reviewId = autoGeneratedReviewId,
        timestampCreated = getResult.value.timestampCreated,
        timestampUpdated = getResult.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == getResult.value)

    }


  }
}

package dev.galre.josue.steamreviews
package repository

import akka.actor.ActorRef
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.{ CreateReview, GetReviewInfo, ReviewState }
import dev.galre.josue.steamreviews.repository.ReviewManagerActor.GetAllReviewsByAuthor
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.duration._
import scala.concurrent.Future
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
  }

}

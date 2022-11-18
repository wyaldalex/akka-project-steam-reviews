package dev.galre.josue.steamreviews
package controller

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.ReviewState
import dev.galre.josue.steamreviews.repository.ReviewManagerActor
import dev.galre.josue.steamreviews.service.utils.Actors.StateManagers
import dev.galre.josue.steamreviews.spec.RoutesSpec

import scala.util.Random
import scala.concurrent.duration._

class ReviewRouterTest extends RoutesSpec {

  Feature("A ReviewRouter Behavior") {

    val reviewId = Math.abs(Random.nextLong())
    val steamAppId = 1444//Math.abs(Random.nextLong())
    val authorId = 1442//Math.abs(Random.nextLong())
    val region = "US/EAST"
    val review = "Good Game"
    val timestampCreated = Math.abs(Random.nextLong())
    val timestampUpdated = Math.abs(Random.nextLong())
    val recommended = true
    val votesHelpful = Math.abs(Random.nextLong())
    val votesFunny = Math.abs(Random.nextLong())
    val weightedVoteScore = Math.abs(Random.nextDouble())
    val commentCount = Math.abs(Random.nextLong())
    val steamPurchase = true
    val receivedForFree = true
    val writtenDuringEarlyAccess = false
    val authorPlaytimeForever = Math.abs(Random.nextDouble())
    val authorPlaytimeLastTwoWeeks = Math.abs(Random.nextDouble())
    val authorPlaytimeAtReview = Math.abs(Random.nextDouble())
    val authorLastPlayed = Math.abs(Random.nextDouble())
    val baseReviewState: ReviewState = ReviewState(
      reviewId, 13131L, 13132L, Some("US/EAST"),
      Some("Good Game"), Some(1668458476L), Some(1668458476L), Some(true), Some(100L), Some(100L),
      Some(78.3), Some(10), Some(true), Some(false), Some(false),
      Some(1), Some(1), Some(1), Some(1)
    )
    implicit val timeout = Timeout(10.seconds)
    val reviewManagerActor: ActorRef = system.actorOf(ReviewManagerActor.props)
    val stateManagers: StateManagers = StateManagers(
      ActorRef.noSender, ActorRef.noSender, reviewManagerActor,
      reviewManagerActor, ActorRef.noSender, ActorRef.noSender, ActorRef.noSender
    )

    val routes: Route = ReviewRouter(stateManagers.Command.review, stateManagers.Query.review).routes

    Scenario("A User sends a CreateReviewRequest") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a Review Information and a POST request to generate Review")
      val createReviewRequest =
        s"""
          |{
          |  "steamAppId": $steamAppId,
          |  "authorId": $authorId,
          |  "region": "$region",
          |  "review": "$review",
          |  "recommended": $recommended,
          |  "votesHelpful": $votesHelpful,
          |  "votesFunny": $votesFunny,
          |  "weightedVoteScore": $weightedVoteScore,
          |  "commentCount": $commentCount,
          |  "steamPurchase": $steamPurchase,
          |  "receivedForFree": $receivedForFree,
          |  "writtenDuringEarlyAccess": $writtenDuringEarlyAccess,
          |  "authorPlaytimeForever": $authorPlaytimeForever,
          |  "authorPlaytimeLastTwoWeeks": $authorPlaytimeLastTwoWeeks,
          |  "authorPlaytimeAtReview": $authorPlaytimeAtReview,
          |  "authorLastPlayed": $authorLastPlayed
          |}
          |""".stripMargin
      val request = Post("/reviews").withEntity(ContentTypes.`application/json`, createReviewRequest)

      When("The User sends a valid CreateReviewRequest")
      request ~!> routes ~> check {

        Then("create review will be returned with 200 ok status")
        assert(status == StatusCodes.OK)
        //assert(entityAs[String] == user)
      }
    }
  }

}

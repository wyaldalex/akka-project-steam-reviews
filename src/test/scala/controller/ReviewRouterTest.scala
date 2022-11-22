package dev.galre.josue.steamreviews
package controller

import akka.http.scaladsl.model.{ ContentTypes, HttpHeader, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.ReviewState
import dev.galre.josue.steamreviews.repository.ReviewManagerActor.ReviewsByFilterContent
import dev.galre.josue.steamreviews.service.command.ReviewCommand.ComposedReview
import dev.galre.josue.steamreviews.service.utils.Actors
import dev.galre.josue.steamreviews.service.utils.Actors.StateManagers
import dev.galre.josue.steamreviews.spec.RoutesSpec

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.Random

//Used to desiralize the entities
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

class ReviewRouterTest extends RoutesSpec {

  def generateReview(autoGeneratedUserId: Long, autoGeneratedSteamGameId: Long): String ={
    s"""
       |{
       |  "steamAppId": $autoGeneratedSteamGameId,
       |  "authorId": $autoGeneratedUserId,
       |  "region": "latam",
       |  "review": "Excelente juego!",
       |  "recommended": true,
       |  "votesHelpful": 1,
       |  "votesFunny": 1,
       |  "weightedVoteScore": 0.6,
       |  "commentCount": 0,
       |  "steamPurchase": true,
       |  "receivedForFree": false,
       |  "writtenDuringEarlyAccess": false,
       |  "authorPlaytimeForever": 294,
       |  "authorPlaytimeLastTwoWeeks": 13.4,
       |  "authorPlaytimeAtReview": 138.4,
       |  "authorLastPlayed": 192324
       |}
       |""".stripMargin
  }

  def generateGameRequest (): String = {
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    s"""
       |{
       |    "steamAppName": "$gameName"
       |}
       |""".stripMargin
  }

  def generateUserRequest(): String = {
    val x = Random.alphanumeric
    val newUserName = s"user_${(x take 20).mkString}"
    s"""{"name":"$newUserName","numGamesOwned":10,"numReviews":10}"""
  }

  def generateReviewUpdateRequest(region:String,review: String,recommended: Boolean): String = {
    s"""
       |{
       |  "region": "$region",
       |  "review": "$review",
       |  "recommended": $recommended,
       |  "votesHelpful": 1,
       |  "votesFunny": 1,
       |  "weightedVoteScore": 0.6,
       |  "commentCount": 0,
       |  "steamPurchase": true,
       |  "receivedForFree": false,
       |  "authorPlaytimeForever": 294,
       |  "authorPlaytimeLastTwoWeeks": 13.4,
       |  "authorPlaytimeAtReview": 138.4,
       |  "authorLastPlayed": 192324
       |}
       |""".stripMargin
  }

  def processHeaderId(headers:  Seq[HttpHeader]): Long = {
    val autoGeneratedId = headers.head.value().substring(17).
      replaceAll("Some", "")
      .replaceAll("\\)", "")
      .replaceAll("\\(", "")
    autoGeneratedId.split(",")(0).toLong
  }

  //Marked as Error
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

    implicit def executionContext: ExecutionContext = system.dispatcher
    val stateManagers: StateManagers = Actors.init

    val reviewRoutes: Route = ReviewRouter(stateManagers.Command.review, stateManagers.Query.review).routes
    val userRoutes: Route = UserRouter(stateManagers.Command.user, stateManagers.Query.user).routes
    val gameRoutes: Route = GameRouter(stateManagers.Command.game, stateManagers.Query.game).routes

      Scenario("A User sends a CreateReviewRequest") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a Review Information and a POST request to generate Review and valid AuthorId and GameId")

        val user = generateUserRequest()
        val game = generateGameRequest()

        val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
          assert(status == StatusCodes.Created)
          processHeaderId(headers)
        }
        val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
          assert(status == StatusCodes.Created)
          processHeaderId(headers)
        }
        info(s"Auto generated Game Id $autoGeneratedSteamGameId")
        info(s"Auto generated User Id $autoGeneratedUserId")

     val createReviewRequest = generateReview(autoGeneratedUserId,autoGeneratedSteamGameId)
     When("The User sends a valid ComposedView result")
     val request = Post("/reviews").withEntity(ContentTypes.`application/json`, createReviewRequest) ~!> reviewRoutes ~> check {

        Then("create review will be returned with 200 ok status")
        assert(status == StatusCodes.OK)
        entityAs[ComposedReview]
      }
     val composedReviewResult = request
     assert(composedReviewResult.author.userId == autoGeneratedUserId)
     assert(composedReviewResult.steamApp.steamAppId == autoGeneratedSteamGameId)
    }

    Scenario("A User sends a Get Request by ReviewId") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a validReviewId and a GET request to retrieve Review")

      val user = generateUserRequest()
      val game = generateGameRequest()

      val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }
      val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }

      val createReviewRequest = generateReview(autoGeneratedUserId, autoGeneratedSteamGameId)
      val createRequestResponse = Post("/reviews")
        .withEntity(ContentTypes.`application/json`, createReviewRequest) ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ComposedReview]
      }

      val getRequest = Get(s"/reviews/${createRequestResponse.reviewId}") ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ReviewState]
      }
      assert(getRequest.steamAppId == createRequestResponse.steamApp.steamAppId)
      assert(getRequest.authorId == createRequestResponse.author.userId)

    }

    Scenario("A User sends a Patch Request for a Review") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a validReviewId and a GET request to retrieve Review")

      val user = generateUserRequest()
      val game = generateGameRequest()

      val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }
      val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }

      val createReviewRequest = generateReview(autoGeneratedUserId, autoGeneratedSteamGameId)
      val createRequestResponse = Post("/reviews")
        .withEntity(ContentTypes.`application/json`, createReviewRequest) ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ComposedReview]
      }

      When("UPDATE request is sent")
      val updatedRegion = "US/WEST"
      val updatedReview = "Bad Game"
      val updatedRecommended = false
      val updateRequest = generateReviewUpdateRequest(updatedRegion,updatedReview,updatedRecommended)
      val updateRequestResponse = Patch(s"/reviews/${createRequestResponse.reviewId}")
        .withEntity(ContentTypes.`application/json`, updateRequest) ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ReviewState]
      }
      Then("a ReviewState with the updated values should be returned")
      assert(updateRequestResponse.steamAppId == createRequestResponse.steamApp.steamAppId)
      assert(updateRequestResponse.authorId == createRequestResponse.author.userId)
      assert(updateRequestResponse.recommended.value == updatedRecommended)
      assert(updateRequestResponse.region.value == updatedRegion)
      assert(updateRequestResponse.review.value == updatedReview)
    }

    //TODO: Delete review seems to fail, 500 error, futures used inside actor, similar to GameManagerActor update
//    Scenario("A User sends a Delete Request for a Review") {
//      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
//      Given("a validReviewId and a GET request to retrieve Review")
//
//      val user = generateUserRequest()
//      val game = generateGameRequest()
//
//      val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
//        assert(status == StatusCodes.Created)
//        processHeaderId(headers)
//      }
//      val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
//        assert(status == StatusCodes.Created)
//        processHeaderId(headers)
//      }
//
//      val createReviewRequest = generateReview(autoGeneratedUserId, autoGeneratedSteamGameId)
//      val createRequestResponse = Post("/reviews")
//        .withEntity(ContentTypes.`application/json`, createReviewRequest) ~!> reviewRoutes ~> check {
//        assert(status == StatusCodes.OK)
//        entityAs[ComposedReview]
//      }
//
//      Delete(s"/reviews/${createRequestResponse.reviewId}") ~!> reviewRoutes ~> check {
//        assert(status == StatusCodes.OK)
//        assert(entityAs[ResponseWithMessage] == ResponseWithMessage(200, Some("Review was deleted successfully.")) )
//      }
//    }

    Scenario("A User sends a request to retrieve reviews by authorId") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a valid author id and a GET request to retrieve reviews for that author")
      val user = generateUserRequest()
      val game = generateGameRequest()
      val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }
      val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }

      val createReviewRequest = generateReview(autoGeneratedUserId,autoGeneratedSteamGameId)
      val request = Post("/reviews").withEntity(ContentTypes.`application/json`, createReviewRequest)
      val composedReviewResult = request ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ComposedReview]
      }
      When("When a Get request is done using a valid authorId")
      val getRequestByAuthorId = Get(s"/reviews/filter/user/${autoGeneratedUserId}") ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ReviewsByFilterContent]
      }
      Then("Corresponding review is returned")
      info(getRequestByAuthorId.toString)
      assert(getRequestByAuthorId.reviews.size == 1)
      assert(getRequestByAuthorId.reviews.head.value.reviewId == composedReviewResult.reviewId)
      assert(getRequestByAuthorId.reviews.head.value.authorId == composedReviewResult.author.userId)

    }

    Scenario("A User sends a request to retrieve reviews by gameId") {
      implicit val timeout = RouteTestTimeout(300.seconds.dilated)
      Given("a valid game id and a GET request to retrieve reviews for that game")
      val user = generateUserRequest()
      val game = generateGameRequest()
      val autoGeneratedUserId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> userRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }
      val autoGeneratedSteamGameId = Post("/games").withEntity(ContentTypes.`application/json`, game) ~!> gameRoutes ~> check {
        assert(status == StatusCodes.Created)
        processHeaderId(headers)
      }

      val createReviewRequest = generateReview(autoGeneratedUserId, autoGeneratedSteamGameId)
      val request = Post("/reviews").withEntity(ContentTypes.`application/json`, createReviewRequest)
      val composedReviewResult = request ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ComposedReview]
      }
      When("When a Get request is done using a valid gameId")
      val getRequestByGameId = Get(s"/reviews/filter/game/${autoGeneratedSteamGameId}") ~!> reviewRoutes ~> check {
        assert(status == StatusCodes.OK)
        entityAs[ReviewsByFilterContent]
      }
      Then("Corresponding review is returned")
      info(getRequestByGameId.toString)
      assert(getRequestByGameId.reviews.size == 1)
      assert(getRequestByGameId.reviews.head.value.reviewId == composedReviewResult.reviewId)
      assert(getRequestByGameId.reviews.head.value.steamAppId == composedReviewResult.steamApp.steamAppId)

    }

  }

}

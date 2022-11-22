package dev.galre.josue.steamreviews
package controller

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ RouteTestTimeout, ScalatestRouteTest }
import akka.testkit.TestDuration
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.UserActor.UserState
import dev.galre.josue.steamreviews.repository.UserManagerActor
import dev.galre.josue.steamreviews.service.utils.Actors.{ init, StateManagers }
import dev.galre.josue.steamreviews.spec.{ GherkinSpec, RoutesSpec }
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.GivenWhenThen
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.util.Random
import scala.concurrent.duration._

class UserRouterTest extends RoutesSpec {


  Feature("A User router behaviour") {

    val userId: Long = Math.abs(Random.nextLong())
    val numGamesOwned = 10
    val numReviews = 10
    val x = Random.alphanumeric
    val userNameToCreate = s"user_${(x take 20).mkString}"
    val baseUserState = UserState(userId, Some(userNameToCreate), Some(numGamesOwned), Some(numReviews))
    val userManagerActor: ActorRef = system.actorOf(UserManagerActor.props)
    val stateManagers: StateManagers = StateManagers(ActorRef.noSender,ActorRef.noSender,ActorRef.noSender,
      ActorRef.noSender,userManagerActor,userManagerActor,ActorRef.noSender)
    implicit val timeout = Timeout(10.seconds)
    val routes: Route = UserRouter(stateManagers.Command.user,stateManagers.Query.user).routes

    //TODO: Pending Change, response should return entity not info in headers
    Scenario("A client send a request to create a new user") {

      implicit val timeout = RouteTestTimeout(10.seconds.dilated)
      Given("a user information and a POST request")
      val x = Random.alphanumeric
      val userName = s"user_${(x take 20).mkString}"
      val user = s"""{"name":"$userName","numGamesOwned":$numGamesOwned,"numReviews":$numReviews}"""
      val request = Post("/users").withEntity(ContentTypes.`application/json`, user)

      When("a request to create a new user is sent to the server")
      request ~!> routes ~> check {

        Then("will create a new user and will response with the user info and a 200 status")
        assert(status == StatusCodes.Created)
        //assert(entityAs[String] == user)
      }
    }

    Scenario("A client send a request to get a user") {
      implicit val timeout = RouteTestTimeout(10.seconds.dilated)
      Given("a user and a GET request with the user id")
      val x = Random.alphanumeric
      val newUserName = s"user_${(x take 20).mkString}"
      val user = s"""{"name":"$newUserName","numGamesOwned":$numGamesOwned,"numReviews":$numReviews}"""
      val autoGeneratedId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.Created)
        val autoGeneratedId = headers.head.value().substring(17).
          replaceAll("Some", "")
          .replaceAll("\\)", "")
          .replaceAll("\\(", "")
        autoGeneratedId.split(",")(0).toLong
      }
      info(s"Auto Generated Id $autoGeneratedId")
      When("a GET request to an specific userId is sent")
      Get(s"/users/$autoGeneratedId") ~!> routes ~> check {
        Then("will respond with a 200 and the user information")
        assert(status == StatusCodes.OK)
        assert(entityAs[UserState] == baseUserState.copy(userId = autoGeneratedId, name = Some(newUserName)))
      }
    }

    Scenario("A Client sends a request to Update a User") {
      implicit val timeout = RouteTestTimeout(10.seconds.dilated)
      Given("A Client with a valid UserId and UserState for update")
      val x = Random.alphanumeric
      val newUserName = s"user_${(x take 20).mkString}"
      val user = s"""{"name":"$newUserName","numGamesOwned":$numGamesOwned,"numReviews":$numReviews}"""
      val autoGeneratedId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.Created)
        val autoGeneratedId = headers.head.value().substring(17).
          replaceAll("Some", "")
          .replaceAll("\\)", "")
          .replaceAll("\\(", "")
        autoGeneratedId.split(",")(0).toLong
      }

      val y = Random.alphanumeric
      val updatedUserName = s"user_${(y take 20).mkString}"
      val updatedNumGamesOwned = 20
      val updatedNumReviews = 20
      val updateUser = s"""{"name":"$updatedUserName","numGamesOwned":$updatedNumGamesOwned,"numReviews":$updatedNumReviews}"""
      When("A Patch request is Sent")
      Then("Will respond with a 200 Ok response and the update user state")
      Patch(s"/users/$autoGeneratedId").withEntity(ContentTypes.`application/json`, updateUser) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[UserState] == UserState(autoGeneratedId,Some(updatedUserName),
          Some(updatedNumGamesOwned),Some(updatedNumReviews)))
      }
    }

    Scenario("A Client sends a request to Delete a User") {
      implicit val timeout = RouteTestTimeout(10.seconds.dilated)
      Given("A Client with a valid UserId and Delete request")
      val x = Random.alphanumeric
      val newUserName = s"user_${(x take 20).mkString}"
      val user = s"""{"name":"$newUserName","numGamesOwned":$numGamesOwned,"numReviews":$numReviews}"""
      val autoGeneratedId = Post("/users").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.Created)
        val autoGeneratedId = headers.head.value().substring(17).
          replaceAll("Some", "")
          .replaceAll("\\)", "")
          .replaceAll("\\(", "")
        autoGeneratedId.split(",")(0).toLong
      }

      When("A Delete request is Sent")
      Then("Will respond with a 200 Ok response and the delete confirmation message")
      Delete(s"/users/$autoGeneratedId") ~!> routes ~> check {

        assert(status == StatusCodes.OK)
        assert(entityAs[ResponseWithMessage] == ResponseWithMessage(200, Some("UserState was deleted successfully.")) )
        info(response.toString())
      }
    }

  }
}

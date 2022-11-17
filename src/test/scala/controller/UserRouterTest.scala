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
import dev.galre.josue.steamreviews.service.utils.Actors.StateManagers
import dev.galre.josue.steamreviews.spec.{ GherkinSpec, RoutesSpec }
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.GivenWhenThen

import scala.util.Random
import scala.concurrent.duration._

class UserRouterTest extends RoutesSpec {


  Feature("A User router behaviour") {

    val userId: Long = Math.abs(Random.nextLong())
    val numGamesOwned: Option[Int] = Some(10)
    val numReviews: Option[Int] = Some(10)
    val x = Random.alphanumeric
    val userName = s"user_${(x take 20).mkString}"
    val baseUserState = UserState(userId, Some(userName), numGamesOwned, numReviews)
    val userManagerActor: ActorRef = system.actorOf(UserManagerActor.props)
    val stateManagers: StateManagers = StateManagers(ActorRef.noSender,ActorRef.noSender,ActorRef.noSender,
      ActorRef.noSender,userManagerActor,userManagerActor,ActorRef.noSender)
    implicit val timeout = Timeout(10.seconds)
    val routes: Route = UserRouter(stateManagers.Command.user,stateManagers.Query.user).routes

    Scenario("A client send a request to create a new user") {

      implicit val timeout = RouteTestTimeout(10.seconds.dilated)
      Given("a user information and a POST request")
      val user = """{"name":"mordho","numGamesOwned":10,"numReviews":10}"""
      val request = Post("/users").withEntity(ContentTypes.`application/json`, user)

      When("a request for create a new user is send to the server")
      request ~!> routes ~> check {

        Then("will be create a new user and will response with the user info and a 200 status")
        assert(status == StatusCodes.Created)
        //assert(entityAs[String] == user)
      }
    }

  }
}

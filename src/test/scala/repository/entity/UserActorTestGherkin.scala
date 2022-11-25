package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import dev.galre.josue.steamreviews.repository.entity.UserActor._
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.util.Random

class UserActorTestGherkin extends GherkinSpec {

  Feature("A UserActor behavior") {
    val userId: Long = Math.abs(Random.nextLong())
    val userName: String = "PlayerUnderground"
    val numGamesOwned: Option[Int] = Some(10)
    val numReviews: Option[Int] = Some(10)
    val userActor: ActorRef = system.actorOf(UserActor.props(userId))
    
    Scenario("Return a Right(UserState) on CreateUser command ") {
      Given("UserActor and a valid values")

      When("CreateUser Command sent")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)

      Then("New UserState should be returned")
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))
    }

    Scenario("Return a Left(String) on an invalid(same name) UpdateUser command") {

      Given("UserActor and a repeated user name for update")

      When("When UpdateUser command sent ")
      userActor ! UpdateUser(userId, Some(userName), numGamesOwned, numReviews)

      Then("Error message should be returned")
      expectMsg(Left("The new name cannot be equal to the previous one."))
    }

    Scenario("Return a Right(UserState) on a valid (different name) UpdateUser command") {
      Given("A UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("UpdateUser command sent")
      userActor ! UpdateUser(userId, Some(userName.concat("-2")), numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName.concat("-2")), numGamesOwned, numReviews)))

      userActor ! UpdateUser(userId, Some(userName), numGamesOwned, numReviews)

      Then("Correct UserState should be returned")
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))
    }

    Scenario("Return current Right(UserState) on empty fields in UpdateUser command") {

      Given("A UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("UpdateUser command sent with empty fields")
      userActor ! UpdateUser(userId, Option.empty, Option.empty, Option.empty)

      Then("The Original UserState should be returned")
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))
    }

    Scenario("Return a Right(value = true) when receiving an AddOneView command") {
      Given("A UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("An AddOneReview command sent ")
      userActor ! AddOneReview(userId)

      Then("A true response should be received")
      expectMsg(Right(true))
    }

    Scenario("Return a Right(value = true) when receiving a RemoveOneReview command") {

      Given("A UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("An RemoveOneReview command sent ")
      userActor ! RemoveOneReview(userId)

      Then("A true response should be received")
      expectMsg(Right(true))
    }

    Scenario("Return the properly updated state when receiving GetUserInfo command") {

      Given("UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("GetUserInfo command sent")
      userActor ! GetUserInfo(userId)

      Then("Correct UserState should be returned")
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))
    }

    Scenario("Recover UserState after a Restart ") {
      Given("UserActor with a UserState")
      userActor ! CreateUser(userName, numGamesOwned, numReviews)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))

      When("A restart happens")
      userActor ! Kill

      Then("UserActor should restore UserState")
      val restartedUserActor = system.actorOf(UserActor.props(userId))
      restartedUserActor ! GetUserInfo(userId)
      expectMsg(Right(UserState(userId, Some(userName), numGamesOwned, numReviews)))
    }

  }

}

package dev.galre.josue.steamreviews
package repository

import akka.actor.{ ActorRef, Kill }
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, DeleteGame, GameState, GetGameInfo, UpdateName }
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.duration._
import scala.util.Random

class GameManagerActorTestGherkin extends GherkinSpec {


  Feature("A GameManager Behavior") {

    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    implicit val timeout: Timeout = Timeout(20.seconds)
    val gameManagerActor: ActorRef = system.actorOf(GameManagerActor.props(timeout, system.dispatcher))


    Scenario("forward valid CreateGame command and return a Right(GameState)") {
      Given("A GameManagerActor and a GameState")

      When("Create Command is sent")
      gameManagerActor ! CreateGame(gameName)

      Then("The correct GameState should be returned")
      val result = expectMsgClass(classOf[Right[String, GameState]])
      assert(result.isRight)
      assert(gameName == result.value.steamAppName)
    }

    Scenario(" return a Left(String) in case the game already exists") {

      Given("A GameManagerActor and a GameState")
      val x = Random.alphanumeric
      val gameName = s"game_${(x take 20).mkString}"

      gameManagerActor ! CreateGame(gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      When("A CreateGame command is sent with a repeated gameName")
      gameManagerActor ! CreateGame(gameName)

      Then("A error message should be returned")
      val createResultDuplicate = expectMsgClass(classOf[Left[String, GameState]])
      assert(createResultDuplicate.isLeft)
    }

    Scenario(" forward on a GetGameInfo(id) command and return a Right(GameState)") {

      Given("A GameManagerActor and a GameState")
      val x = Random.alphanumeric
      val gameName = s"game_${(x take 20).mkString}"
      gameManagerActor ! CreateGame(gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      When("A GetGameInfo is sent with corresponding id is sent")
      gameManagerActor ! GetGameInfo(createResult.value.steamAppId)

      Then("The correct GameState should be returned")
      val getResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(getResult.isRight)
      assert(gameName == getResult.value.steamAppName)
    }

    Scenario("return a Right(true) on a valid DeleteGame command ") {

      val x = Random.alphanumeric
      val gameNameToDelete = s"game_${(x take 20).mkString}"

      gameManagerActor ! CreateGame(gameNameToDelete)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      gameManagerActor ! DeleteGame(createResult.value.steamAppId)
      val deleteResponse = expectMsgClass(classOf[Right[String, Boolean]])

      assert(deleteResponse.value == true)
    }

    Scenario("return a Right(GameState) with the updated state") {

      Given("A GameManagerActor and a GameState")
      val x = Random.alphanumeric
      val gameName = s"game_${(x take 20).mkString}"
      gameManagerActor ! CreateGame(gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      When("A UpdateName is sent")
      val newName = gameName.concat("-2")
      gameManagerActor ! UpdateName(createResult.value.steamAppId, newName)
      //val updateResult = expectMsgClass(20 seconds,classOf[Right[String, GameState]])
      //assert(updateResult.isRight)
      //assert(updateResult.value.steamAppName == newName)
      Then("The correct GameState should be returned in a GetGameInfo command")
      gameManagerActor ! GetGameInfo(createResult.value.steamAppId)
      val getResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(getResult.isRight)

      assert(getResult.value.steamAppName == newName)
    }
  }
}

class GameManagerActorRecoverTestGherkin extends GherkinSpec {

  Feature("A GameManagerActor  restart behaviour") {
    Scenario("Restart GameManagerActor") {
      Given("A GameManagerActor and a GameState with a history of changes")
      val x = Random.alphanumeric
      val gameName = s"game_${(x take 20).mkString}"
      implicit val timeout: Timeout = Timeout(20.seconds)
      val gameManagerActor: ActorRef = system.actorOf(GameManagerActor.props(timeout, system.dispatcher))

      gameManagerActor ! CreateGame(gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      val newName = gameName.concat("-2")
      gameManagerActor ! UpdateName(createResult.value.steamAppId, newName)
      gameManagerActor ! DeleteGame(createResult.value.steamAppId)

      When("When GameManagerActor is restarted")
      gameManagerActor ! Kill
      val restartedUserActor = system.actorOf(GameManagerActor.props(timeout, system.dispatcher))

      Then("The correct GameState should be recovered")
      restartedUserActor ! GetGameInfo(createResult.value.steamAppId)
      val restartedResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(restartedResult.isRight)
      assert(restartedResult.value.steamAppName == newName)
    }

  }
}

package dev.galre.josue.steamreviews
package repository

import akka.actor.{ ActorRef, Kill }
import akka.util.Timeout
import dev.galre.josue.steamreviews.generators.PayLoadGenerator._
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, DeleteGame, GameState, GetGameInfo }
import dev.galre.josue.steamreviews.repository.GameManagerActor.CreateGameFromCSV
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.duration._
import scala.util.Random

class GameManagerActorTestGherkin extends GherkinSpec {


  Feature("A GameManager Behavior") {

    val gameName = generateRandomString(prefix = "game")
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

    //TODO: Related to the use of ask pattern inside an actor
//    Scenario("forward invalid CreateGame command and return a Right(GameState)") {
//      Given("A GameManagerActor and a GameState")
//
//      When("Create Command is sent")
//      gameManagerActor ! CreateGame("")
//      expectMsgClass(classOf[Right[String, GameState]])
//      gameManagerActor ! CreateGame("")
//      expectMsgClass(classOf[Right[String, GameState]])
//      gameManagerActor ! CreateGame("")
//      expectMsgClass(classOf[Right[String, GameState]])
//
//
////      assert(result.isRight)
////      assert(gameName == result.value.steamAppName)
//    }

//        Scenario("forward invalid CreateGame command and return a Right(GameState)") {
//          Given("A GameManagerActor and a GameState")
//
//          When("Create Command is sent")
//          gameManagerActor ! CreateGame("")
//          expectMsgClass(classOf[Right[String, GameState]])
//          gameManagerActor ! CreateGame("")
//          expectMsgClass(classOf[Right[String, GameState]])
//          gameManagerActor ! CreateGame("")
//          expectMsgClass(classOf[Right[String, GameState]])
//
//
//    //      assert(result.isRight)
//    //      assert(gameName == result.value.steamAppName)
//        }

    Scenario("forward valid CreateGameFromCSV command and return a Right(GameState)") {
      Given("A GameManagerActor and a GameState")
      val gameName = generateRandomString("game")
      val steamAppId: Long =  Math.abs(Random.nextLong())

      When("Create Command is sent")
      gameManagerActor ! CreateGameFromCSV(GameState(steamAppId,gameName))
      expectNoMessage()

      Then("The correct GameState should be returned on request")
      gameManagerActor ! GetGameInfo(steamAppId)
      val getResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(getResult.isRight)
      assert(gameName == getResult.value.steamAppName)
    }

    Scenario(" return a Left(String) in case the game already exists") {

      Given("A GameManagerActor and a GameState")
      val gameName = generateRandomString("game")

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
      val gameName = generateRandomString(prefix = "game")
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

      val gameNameToDelete = generateRandomString("game")

      gameManagerActor ! CreateGame(gameNameToDelete)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      gameManagerActor ! DeleteGame(createResult.value.steamAppId)
      val deleteResponse = expectMsgClass(classOf[Right[String, Boolean]])

      assert(deleteResponse.value == true)
    }

    //TODO: Related to the use of ask pattern inside actors
    //Error
//    Scenario("return a Right(GameState) with the updated state") {
//
//      Given("A GameManagerActor and a GameState")
//      val x = Random.alphanumeric
//      val gameName = s"game_${(x take 20).mkString}"
//      gameManagerActor ! CreateGame(gameName)
//      val createResult = expectMsgClass(classOf[Right[String, GameState]])
//      assert(createResult.isRight)
//      val autoGeneratedSteamAppId = createResult.value.steamAppId
//      info(s"Steam App Id $autoGeneratedSteamAppId")
//
//      //Error caused by use of Futures in a ReceiveCommand
//      When("A UpdateName is sent")
//      val y = Random.alphanumeric
//      val newName = s"game_${(y take 20).mkString}"
//      gameManagerActor ! UpdateName(autoGeneratedSteamAppId, newName)
//      expectNoMessage()
//      //val updateResult = expectMsgClass(20 seconds,classOf[Right[String, GameState]])
//      //assert(updateResult.isRight)
//      //assert(updateResult.value.steamAppName == newName)
//      Then("The correct GameState should be returned in a GetGameInfo command")
//      gameManagerActor ! GetGameInfo(createResult.value.steamAppId)
//      val getResult = expectMsgClass(classOf[Right[String, GameState]])
//      assert(getResult.isRight)
//
//      assert(getResult.value.steamAppName == newName)
//    }

    Scenario("Restart GameManagerActor") {
      Given("A GameManagerActor and a GameState with a history of changes")

      val gameName = generateRandomString("game")

      gameManagerActor ! CreateGame(gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)
      val autoGeneratedSteamId = createResult.value.steamAppId
      info(s"Steam App Id $autoGeneratedSteamId")

      When("When GameManagerActor is restarted")
      gameManagerActor ! Kill
      val restartedGameManagerActor = system.actorOf(GameManagerActor.props(timeout, system.dispatcher))

      Then("The correct GameState should be recovered")
      restartedGameManagerActor ! GetGameInfo(autoGeneratedSteamId)
      val restartedResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(restartedResult.isRight)
      assert(gameName == restartedResult.value.steamAppName)
    }

  }
}

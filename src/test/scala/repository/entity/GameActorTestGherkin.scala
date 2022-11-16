package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, GameState, GetGameInfo, UpdateName }
import dev.galre.josue.steamreviews.spec.{ GherkinSpec, UnitSpec }
import org.scalatest.Outcome

import scala.util.Random

class GameActorTestGherkin extends GherkinSpec {

  Feature("A GameActor behavior") {

    val gameActorId: Long = Math.abs(Random.nextLong())
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    val gameActor: ActorRef = system.actorOf(GameActor.props(gameActorId))

    Scenario("Create GameState ") {
      Given(" A GameActor and a game name ")
      info(s"Using name $gameName")
      When("CreateGame command sent")
      gameActor ! CreateGame(gameName)

      Then("the GameActor should Return a Right(GameState)")
      expectMsg(Right(GameState(gameActorId,gameName)))

    }

    Scenario("Game with Same Name should Fail") {
      val x = Random.alphanumeric
      val gameName2 = s"game_${(x take 20).mkString}"
      Given("A GameActor and a repeated game name")
      info(s"Using name $gameName2")

      When("UpdateName command with a repeated game name")
      gameActor ! CreateGame(gameName2)
      expectMsg(Right(GameState(gameActorId, gameName2)))
      gameActor ! UpdateName(gameActorId, gameName2)

      Then("The GameActor should return Return a string with the error message ")
      expectMsg(Left("The new name cannot be equal to the previous one."))
    }

    Scenario("Return a Right(GameState) on a valid(different name) UpdateName command") {
      val x = Random.alphanumeric
      val newName = s"game_${(x take 20).mkString}"
      Given("A Game actor and a valid new name for update")

      When("UpdateName command with new valid name is sent")
      gameActor ! CreateGame(gameName)
      expectMsg(Right(GameState(gameActorId,gameName)))
      gameActor ! UpdateName(gameActorId, newName)

      Then("Return the new GameState")
      expectMsg(Right(GameState(gameActorId, newName)))
    }
    
    Scenario("A game actor should recover state in case of restart") {
      Given("A Game actor with changes in state")
      gameActor ! CreateGame(gameName)
      expectMsg(Right(GameState(gameActorId, gameName)))
      gameActor ! UpdateName(gameActorId, gameName.concat("-2"))
      expectMsg(Right(GameState(gameActorId, gameName.concat("-2"))))
      gameActor ! UpdateName(gameActorId, gameName)
      expectMsg(Right(GameState(gameActorId, gameName)))

      When("Some event triggers a restart")
      gameActor ! Kill

      Then("GameActor should recover the state")
      val restartedGameActor = system.actorOf(GameActor.props(gameActorId))
      restartedGameActor ! GetGameInfo(gameActorId)
      expectMsg(Right(GameState(gameActorId, gameName)))
    }
  }
}

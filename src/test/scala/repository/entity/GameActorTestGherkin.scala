package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.ActorRef
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, GameState, UpdateName }
import dev.galre.josue.steamreviews.spec.{ GherkinSpec, UnitSpec }
import org.scalatest.Outcome

import scala.util.Random

class GameActorTestGherkin extends GherkinSpec {

  Feature("A GameActor") {

    val gameActorId: Long = Math.abs(Random.nextLong())
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    val gameActor: ActorRef = system.actorOf(GameActor.props(gameActorId))

    Scenario("Create GameState ") {
      info(s"Using game name $gameName")
      Given(" A GameActor and a game name ")

      When("CreateGame command sent")
      gameActor ! CreateGame(gameName)

      Then("the GameActor should Return a Right(GameState)")
      expectMsg(Right(GameState(gameActorId,gameName)))

    }

    Scenario("Game with Same Name should Fail") {
      info(s"Using game name $gameName")
      Given("A GameActor and a repeated game name")

      When("UpdateName command with a repeated game name")
      gameActor ! CreateGame(gameName)
      expectMsg(Right(GameState(gameActorId, gameName)))
      gameActor ! UpdateName(gameActorId, gameName)

      Then("The GameActor should return Return a string with the error message ")
      expectMsg(Left("The new name cannot be equal to the previous one."))
    }
  }
}

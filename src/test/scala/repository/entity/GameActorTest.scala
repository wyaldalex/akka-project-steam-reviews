package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.ActorRef
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, GameState }
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

import scala.util.Random


class GameActorTest extends UnitSpec {

  case class FixtureParam(actor: ActorRef, probe: TestProbe, gameName: String, id: Long)
  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val randomId: Long = Math.abs(Random.nextLong())
    val gameName: String = "Mordhau"
    val probe: TestProbe = TestProbe()
    val gameActor: ActorRef = system.actorOf(GameActor.props(randomId))
    val theFixture = FixtureParam(gameActor, probe, gameName,randomId)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A Game Actor" should "Return a game state in " in { f =>
    f.actor ! CreateGame(f.gameName)
    expectMsg(Right(GameState(f.id,f.gameName)))
  }

}

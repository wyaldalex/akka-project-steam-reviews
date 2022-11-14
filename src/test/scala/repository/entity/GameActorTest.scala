package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, GameState, GetGameInfo, UpdateName }
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

import scala.util.Random


class GameActorTest extends UnitSpec {

  case class FixtureParam(actor: ActorRef, probe: TestProbe, gameName: String, id: Long)
  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val gameActorId: Long =  Math.abs(Random.nextLong())
    val gameName: String = "Mordhau"
    val probe: TestProbe = TestProbe()
    val gameActor: ActorRef = system.actorOf(GameActor.props(gameActorId))
    val theFixture = FixtureParam(gameActor, probe, gameName,gameActorId)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A Game Actor" should "Return a Right(GameState) on CreateGame command " in { f =>
    f.actor ! CreateGame(f.gameName)
    expectMsg(Right(GameState(f.id,f.gameName)))
  }

  it should "Return a Left(String) on an invalid(same name) UpdateName command " in {
    f =>
      f.actor ! CreateGame(f.gameName)
      expectMsg(Right(GameState(f.id, f.gameName)))

      f.actor ! UpdateName(f.id, f.gameName)
      expectMsg(Left("The new name cannot be equal to the previous one."))
     // expectMsgClass(Left.getClass)
     // expectMsgType[Left]
  }

  it should "Return a Right(GameState) on a valid(different name) UpdateName command " in { f =>

    f.actor ! CreateGame(f.gameName)
    expectMsg(Right(GameState(f.id, f.gameName)))

    f.actor ! UpdateName(f.id, f.gameName.concat("-2"))
    expectMsg(Right(GameState(f.id, f.gameName.concat("-2"))))

    f.actor ! UpdateName(f.id, f.gameName)
    expectMsg(Right(GameState(f.id, f.gameName)))

  }

  it should "Recover GameState after a Restart" in { f =>

    f.actor ! CreateGame(f.gameName)
    expectMsg(Right(GameState(f.id, f.gameName)))

    f.actor ! UpdateName(f.id, f.gameName.concat("-2"))
    expectMsg(Right(GameState(f.id, f.gameName.concat("-2"))))

    f.actor ! UpdateName(f.id, f.gameName)
    expectMsg(Right(GameState(f.id, f.gameName)))

    f.actor ! Kill
    val restartedGameActor = system.actorOf(GameActor.props(f.id))
    restartedGameActor ! GetGameInfo(f.id)
    expectMsg(Right(GameState(f.id, f.gameName)))
  }

}

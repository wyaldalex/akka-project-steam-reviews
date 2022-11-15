package dev.galre.josue.steamreviews
package repository

import akka.actor.{ ActorRef, Kill }
import akka.testkit.TestProbe
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.GameActor.{ CreateGame, DeleteGame, GameState, GetGameInfo, UpdateName }
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class GameManagerActorTest extends UnitSpec {

  case class FixtureParam(
    actor: ActorRef, probe: TestProbe, gameName: String, timeout: Timeout
  )

  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val probe: TestProbe = TestProbe()
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    implicit val timeout: Timeout = Timeout(20.seconds)
    val gameManagerActor: ActorRef = system.actorOf(GameManagerActor.props(timeout,system.dispatcher))
    val theFixture = FixtureParam(gameManagerActor, probe, gameName, timeout)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A GameManager actor" should " forward valid CreateGame command and return a Right(GameState) " in {
    f =>
      f.actor ! CreateGame(f.gameName)
      val result = expectMsgClass(classOf[Right[String, GameState]])
      assert(result.isRight)

      assert(f.gameName == result.value.steamAppName)
  }

  it should " return a Left(String) in case the game already exists" in { f =>
    f.actor ! CreateGame(f.gameName)
    val createResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(createResult.isRight)

    f.actor ! CreateGame(f.gameName)
    val createResultDuplicate = expectMsgClass(classOf[Left[String, GameState]])
    assert(createResultDuplicate.isLeft)
  }

  it should " forward on a GetGameInfo(id) command and return a Right(GameState)" in {
    f =>
      f.actor ! CreateGame(f.gameName)
      val createResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(createResult.isRight)

      f.actor ! GetGameInfo(createResult.value.steamAppId)
      val getResult = expectMsgClass(classOf[Right[String, GameState]])
      assert(getResult.isRight)

      assert(f.gameName == getResult.value.steamAppName)
  }

  it should "return a Right(GameState) with the updated state" in { f=>
    f.actor ! CreateGame(f.gameName)
    val createResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(createResult.isRight)

    val newName = f.gameName.concat("-2")
    f.actor ! UpdateName(createResult.value.steamAppId,newName)
    //val updateResult = expectMsgClass(20 seconds,classOf[Right[String, GameState]])
    //assert(updateResult.isRight)
    //assert(updateResult.value.steamAppName == newName)
    f.actor ! GetGameInfo(createResult.value.steamAppId)
    val getResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(getResult.isRight)

    assert(getResult.value.steamAppName == newName)

  }

  it should "return a Right(true) on a valid DeleteGame command " in { f=>
    f.actor ! CreateGame(f.gameName)
    val createResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(createResult.isRight)

    f.actor ! DeleteGame(createResult.value.steamAppId)
    val deleteResponse = expectMsgClass(classOf[Right[String, Boolean]])

    assert(deleteResponse.value == true)
  }

//  it should "Recover GameManager state after a Restart " in { f=>
//
//    f.actor ! CreateGame(f.gameName)
//    val createResult = expectMsgClass(classOf[Right[String, GameState]])
//    assert(createResult.isRight)
//
//    val newName = f.gameName.concat("-2")
//    f.actor ! UpdateName(createResult.value.steamAppId, newName)
//    f.actor ! DeleteGame(createResult.value.steamAppId)
//
//
//    f.actor ! Kill
//
//    val restartedUserActor = system.actorOf(GameManagerActor.props(f.timeout,system.dispatcher))
//
//    restartedUserActor ! GetGameInfo(createResult.value.steamAppId)
//    val restartedResult = expectMsgClass(classOf[Right[String, GameState]])
//    assert(restartedResult.isRight)
//    assert(restartedResult.value.steamAppName == newName)
//  }

}

class GameManagerActorRecoverTest extends UnitSpec {

  case class FixtureParam(
    actor: ActorRef, probe: TestProbe, gameName: String, timeout: Timeout
  )

  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val probe: TestProbe = TestProbe()
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    implicit val timeout: Timeout = Timeout(20.seconds)
    val userActor: ActorRef = system.actorOf(GameManagerActor.props(timeout,system.dispatcher))
    val theFixture = FixtureParam(userActor, probe, gameName, timeout)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }
  "A GameManager actor" should "Recover GameManager state after a Restart " in { f=>

    f.actor ! CreateGame(f.gameName)
    val createResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(createResult.isRight)

    val newName = f.gameName.concat("-2")
    f.actor ! UpdateName(createResult.value.steamAppId, newName)
    f.actor ! DeleteGame(createResult.value.steamAppId)


    f.actor ! Kill

    val restartedUserActor = system.actorOf(GameManagerActor.props(f.timeout,system.dispatcher))

    restartedUserActor ! GetGameInfo(createResult.value.steamAppId)
    val restartedResult = expectMsgClass(classOf[Right[String, GameState]])
    assert(restartedResult.isRight)
    assert(restartedResult.value.steamAppName == newName)
  }

}

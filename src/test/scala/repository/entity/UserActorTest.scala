package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.UserActor._
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

class UserActorTest extends UnitSpec {
  case class FixtureParam(actor: ActorRef, probe: TestProbe, userId: Long, userName: String,
    numGamesOwned: Option[Int], numReviews: Option[Int])

  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val userId: Long = 554771653155793366L
    val userName: String = "PlayerUnderground"
    val numGamesOwned: Option[Int] = Some(10)
    val numReviews: Option[Int] = Some(10)
    val probe: TestProbe = TestProbe()
    val userActor: ActorRef = system.actorOf(UserActor.props(userId))
    val theFixture = FixtureParam(userActor, probe, userId, userName, numGamesOwned, numReviews)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A UserActor " should "Return a Right(UserState) on CreateUser command " in { f =>
    f.actor ! CreateUser(f.userName,f.numGamesOwned,f.numReviews)
    expectMsg(Right(UserState(f.userId,Some(f.userName),f.numGamesOwned,f.numReviews)))
  }

  it should "Return a Left(String) on an invalid(same name) UpdateUser command" in { f=>
    f.actor ! UpdateUser(f.userId,Some(f.userName), f.numGamesOwned,f.numReviews)
    expectMsg(Left("The new name cannot be equal to the previous one."))
  }

  it should "Return a Right(UserState) on a valid (different name) UpdateUser command" in {
    f =>

      f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
      expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

      f.actor ! UpdateUser(f.userId, Some(f.userName.concat("-2")), f.numGamesOwned, f.numReviews)
      expectMsg(Right(UserState(f.userId,Some(f.userName.concat("-2")),f.numGamesOwned,f.numReviews)))

      f.actor ! UpdateUser(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)
      expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

  }

  it should "Return current Right(UserState) on empty fields in UpdateUser command" in {
    f =>

      f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
      expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

      f.actor ! UpdateUser(f.userId, Option.empty, Option.empty, Option.empty)
      expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

  }

  it should "Return a Right(value = true) when receiving an AddOneView command" in { f =>

    f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
    expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

    f.actor ! AddOneReview(f.userId)
    expectMsg(Right(true))
  }

  it should "Return a Right(value = true) when receiving a RemoveOneReview command" in { f =>

    f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
    expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

    f.actor ! RemoveOneReview(f.userId)
    expectMsg(Right(true))
  }

  it should "Return the properly updated state when receiving GetUserInfo command" in { f =>

    f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
    expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

    f.actor ! GetUserInfo(f.userId)
    expectMsg(Right(UserState(f.userId,Some(f.userName),f.numGamesOwned,f.numReviews)))
  }

  it should "Recover UserState after a Restart " in { f =>

    f.actor ! CreateUser(f.userName, f.numGamesOwned, f.numReviews)
    expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))

    f.actor ! Kill
    val restartedUserActor = system.actorOf(UserActor.props(f.userId))
    restartedUserActor ! GetUserInfo(f.userId)
    expectMsg(Right(UserState(f.userId, Some(f.userName), f.numGamesOwned, f.numReviews)))
  }

}

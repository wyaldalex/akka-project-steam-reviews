package dev.galre.josue.steamreviews
package repository

import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.{ CreateReview, ReviewState }
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

import scala.concurrent.duration._
import scala.util.Random

class ReviewManagerActorTest extends UnitSpec {

  case class FixtureParam(
    actor: ActorRef, probe: TestProbe, review: ReviewState, timeout: Timeout
  )

  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup

    val reviewId: Long = Math.abs(Random.nextLong())
    val reviewState: ReviewState = ReviewState(
      reviewId, 13131L, 13132L, Some("US/EAST"),
      Some("Good Game"), Some(1668458476L), Some(1668458476L), Some(true), Some(100L), Some(100L),
      Some(78.3), Some(10), Some(true), Some(false), Some(false),
      Some(1), Some(1), Some(1), Some(1)
    )

    val probe: TestProbe = TestProbe()
    val x = Random.alphanumeric
    val gameName = s"game_${(x take 20).mkString}"
    implicit val timeout: Timeout = Timeout(20.seconds)
    val reviewManagerActor: ActorRef = system.actorOf(ReviewManagerActor.props(timeout, system.dispatcher))
    val theFixture = FixtureParam(reviewManagerActor, probe, reviewState, timeout)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A ReviewManager Actor" should " return a Right(ReviewState) on a valida CreateReview command " in {
    f =>
      f.actor ! CreateReview(f.review)
      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)
      val originalStateExceptTimestamps = f.review.copy(
        reviewId = result.value.reviewId,
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)

  }

}

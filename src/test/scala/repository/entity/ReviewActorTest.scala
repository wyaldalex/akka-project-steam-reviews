package dev.galre.josue.steamreviews
package repository.entity

import akka.actor.{ ActorRef, Kill }
import akka.testkit.TestProbe
import dev.galre.josue.steamreviews.repository.entity.ReviewActor.{ CreateReview, GetReviewInfo, ReviewState, UpdateReview }
import dev.galre.josue.steamreviews.spec.UnitSpec
import org.scalatest.Outcome

import scala.util.Random

class ReviewActorTest extends UnitSpec {

  case class FixtureParam(
    actor: ActorRef, probe: TestProbe, reviewId: Long, reviewState: ReviewState
  )

  override def withFixture(test: OneArgTest): Outcome = {
    // Perform setup
    val reviewId: Long = Math.abs(Random.nextLong())
    val reviewState: ReviewState = ReviewState(reviewId, 13131L, 13132L, Some("US/EAST"),
      Some("Good Game"), Some(1668458476L), Some(1668458476L), Some(true), Some(100L), Some(100L),
      Some(78.3), Some(10), Some(true), Some(false), Some(false),
       Some(1), Some(1), Some(1), Some(1))
    val probe: TestProbe = TestProbe()
    val userActor: ActorRef = system.actorOf(ReviewActor.props(reviewId))
    val theFixture = FixtureParam(userActor, probe, reviewId, reviewState)

    super.withFixture(test.toNoArgTest(theFixture)) // Invoke the test function
  }

  "A ReviewActor " should " Return a Right(ReviewState) when receiving a valid CreateReview command, " +
    "only updated and created timestamps should be different from original payload" in { f =>
    f.actor ! CreateReview(f.reviewState)
    val result = expectMsgClass(classOf[Right[String,ReviewState]])
    assert(result.isRight)

    val originalStateExceptTimestamps = f.reviewState.copy(
      timestampCreated = result.value.timestampCreated,
      timestampUpdated = result.value.timestampUpdated)
    assert(originalStateExceptTimestamps == result.value)
  }

  it should "Return a Left(String) when receiving an invalid CreateReview command" in {
    f =>
      f.actor ! CreateReview(f.reviewState.copy(review = Option.empty))
      val result = expectMsgClass(classOf[Left[String, ReviewState]])
      assert(result.isLeft)
  }

  it should "Return a Right(ReviewState) when receiving a valid UpdateReview command " in {
    f =>
      val updatedReceiveForFree = Some(true)
      val updatedRegion = Some("EAST/ASIA")
      val updatedReview = Some("Bad Game")
      val updatedRecommended  = Some(false)
      val updatedVotesHelpful = Some(111L)
      val updatedVotesFunny = Some(111L)
      val updatedWeightedVoteScore = Some(33.33)
      val updatedCommentCount = Some(111L)
      val updatedAuthorPlaytimeForever = Some(2.0)
      val updatedAuthorPlaytimeLastTwoWeeks = Some(2.0)
      val updatedAuthorPlaytimeAtReview = Some(2.0)
      val updatedNewAuthorLastPlayed = Some(2.0)

      f.actor ! CreateReview(f.reviewState)
      expectMsgClass(classOf[Right[String,ReviewState]])

      f.actor ! UpdateReview(f.reviewState.copy(
        receivedForFree = updatedReceiveForFree, region = updatedRegion,
        review = updatedReview, recommended = updatedRecommended, votesHelpful = updatedVotesHelpful,
        votesFunny = updatedVotesFunny,weightedVoteScore = updatedWeightedVoteScore,commentCount = updatedCommentCount,
        authorPlaytimeForever = updatedAuthorPlaytimeForever, authorPlaytimeLastTwoWeeks = updatedAuthorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = updatedAuthorPlaytimeAtReview, authorLastPlayed = updatedNewAuthorLastPlayed
      ))

      val result = expectMsgClass(classOf[Right[String, ReviewState]])
      assert(result.isRight)

      val originalStateExceptTimestamps = f.reviewState.copy(
        receivedForFree = updatedReceiveForFree, region = updatedRegion,
        review = updatedReview, recommended = updatedRecommended, votesHelpful = updatedVotesHelpful,
        votesFunny = updatedVotesFunny, weightedVoteScore = updatedWeightedVoteScore, commentCount = updatedCommentCount,
        authorPlaytimeForever = updatedAuthorPlaytimeForever, authorPlaytimeLastTwoWeeks = updatedAuthorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = updatedAuthorPlaytimeAtReview, authorLastPlayed = updatedNewAuthorLastPlayed,
        timestampCreated = result.value.timestampCreated,
        timestampUpdated = result.value.timestampUpdated
      )
      assert(originalStateExceptTimestamps == result.value)
  }

  it should "Return the properly updated state when receiving GetReviewInfo command" in { f =>

    f.actor ! CreateReview(f.reviewState)
    expectMsgClass(classOf[Right[String, ReviewState]])

    f.actor ! GetReviewInfo(f.reviewId)
    val result = expectMsgClass(classOf[Right[String, ReviewState]])
    assert(result.isRight)

    val originalStateExceptTimestamps = f.reviewState.copy(
      timestampCreated = result.value.timestampCreated,
      timestampUpdated = result.value.timestampUpdated
    )
    assert(originalStateExceptTimestamps == result.value)
  }

  it should "Handle empty options and return the original state on UpdateReview command " in { f=>
    f.actor ! CreateReview(f.reviewState)
    expectMsgClass(classOf[Right[String, ReviewState]])

    f.actor ! UpdateReview(f.reviewState.copy(
      receivedForFree = Option.empty, region = Option.empty,
      review = Option.empty, recommended = Option.empty, votesHelpful = Option.empty,
      votesFunny = Option.empty, weightedVoteScore = Option.empty, commentCount = Option.empty,
      authorPlaytimeForever = Option.empty, authorPlaytimeLastTwoWeeks = Option.empty,
      authorPlaytimeAtReview = Option.empty, authorLastPlayed = Option.empty
    ))

    val result = expectMsgClass(classOf[Right[String, ReviewState]])
    assert(result.isRight)

    val originalStateExceptTimestamps = f.reviewState.copy(
      timestampCreated = result.value.timestampCreated,
      timestampUpdated = result.value.timestampUpdated
    )
    assert(originalStateExceptTimestamps == result.value)

  }

  it should "Recover ReviewState after a Restart" in { f =>

    f.actor ! CreateReview(f.reviewState)
    expectMsgClass(classOf[Right[String, ReviewState]])

    f.actor ! UpdateReview(f.reviewState.copy(
      receivedForFree = Some(true)
    ))
    val resultUpdate = expectMsgClass(classOf[Right[String, ReviewState]])
    assert(resultUpdate.isRight)

    f.actor ! Kill

    val restartedReviewActor = system.actorOf(ReviewActor.props(f.reviewId))
    restartedReviewActor ! GetReviewInfo(f.reviewId)
    val recoverResult = expectMsgClass(classOf[Right[String, ReviewState]])
    assert(recoverResult.isRight)

    val originalStateExceptTimestamps = f.reviewState.copy(
      timestampCreated = recoverResult.value.timestampCreated,
      timestampUpdated = recoverResult.value.timestampUpdated ,
      receivedForFree = recoverResult.value.receivedForFree
    )
    assert(originalStateExceptTimestamps == recoverResult.value)
  }



}

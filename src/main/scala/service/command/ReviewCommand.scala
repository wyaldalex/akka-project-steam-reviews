package dev.galre.josue.steamreviews
package service.command

import repository.ReviewManagerActor.CreateReviewFromCSV
import repository.entity.GameActor._
import repository.entity.ReviewActor._
import repository.entity.UserActor._

import ReviewCommand._
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import cats.data.EitherT

import scala.concurrent.{ ExecutionContext, Future }

object ReviewCommand {

  final case class BasicUser(userId: Long, name: Option[String] = None)

  object ComposedReview {
    def apply(review: ReviewState, game: GameState, user: UserState): ComposedReview =
      ComposedReview(
        reviewId = review.reviewId,
        steamApp = game,
        author = BasicUser(user.userId, user.name),
        region = review.region,
        timestampCreated = review.timestampCreated,
        timestampUpdated = review.timestampUpdated,
        review = review.review,
        recommended = review.recommended,
        votesHelpful = review.votesHelpful,
        votesFunny = review.votesFunny,
        weightedVoteScore = review.weightedVoteScore,
        commentCount = review.commentCount,
        steamPurchase = review.steamPurchase,
        receivedForFree = review.receivedForFree,
        writtenDuringEarlyAccess = review.writtenDuringEarlyAccess,
        authorPlaytimeForever = review.authorPlaytimeForever,
        authorPlaytimeLastTwoWeeks = review.authorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = review.authorPlaytimeAtReview,
        authorLastPlayed = review.authorLastPlayed
      )
  }

  final case class ComposedReview(
    reviewId: Long,
    steamApp: GameState,
    author: BasicUser,
    region: Option[String] = None,
    review: Option[String] = None,
    timestampCreated: Option[Long] = None,
    timestampUpdated: Option[Long] = None,
    recommended: Option[Boolean] = None,
    votesHelpful: Option[Long] = None,
    votesFunny: Option[Long] = None,
    weightedVoteScore: Option[Double] = None,
    commentCount: Option[Long] = None,
    steamPurchase: Option[Boolean] = None,
    receivedForFree: Option[Boolean] = None,
    writtenDuringEarlyAccess: Option[Boolean] = None,
    authorPlaytimeForever: Option[Double] = None,
    authorPlaytimeLastTwoWeeks: Option[Double] = None,
    authorPlaytimeAtReview: Option[Double] = None,
    authorLastPlayed: Option[Double] = None,
  )

  def props(
    gameManagerActor: ActorRef,
    userManagerActor: ActorRef,
    reviewManagerActor: ActorRef
  )
    (implicit timeout: Timeout, executionContext: ExecutionContext): Props =
    Props(
      new ReviewCommand(
        gameManagerActor, userManagerActor, reviewManagerActor
      )
    )
}

class ReviewCommand(
  gameManagerActor: ActorRef, userManagerActor: ActorRef, reviewManagerActor: ActorRef
)(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends Actor
  with ActorLogging {


  def getComposedReview(reviewId: Long): Future[Either[String, ComposedReview]] = {
    val composedReviewFuture = for {
      review <- EitherT((reviewManagerActor ? GetReviewInfo(reviewId)).mapTo[ReviewCreatedResponse])
      game <- EitherT((gameManagerActor ? GetGameInfo(review.steamAppId)).mapTo[GetGameInfoResponse])
      user <- EitherT((userManagerActor ? GetUserInfo(review.authorId)).mapTo[GetUserInfoResponse])
    } yield {
      ComposedReview(review, game, user)
    }

    composedReviewFuture.value
  }

  override def receive: Receive = {

    // All review messages
    case createCommand @ CreateReview(review) =>
      val steamAppId = review.steamAppId
      val authorId = review.authorId

      val createReviewFuture = for {
        game <- EitherT((gameManagerActor ? GetGameInfo(steamAppId)).mapTo[GetGameInfoResponse])
        user <- EitherT((userManagerActor ? GetUserInfo(authorId)).mapTo[GetUserInfoResponse])
        review <- EitherT((reviewManagerActor ? createCommand).mapTo[ReviewCreatedResponse])
        _ <- EitherT((userManagerActor ? AddOneReview(authorId)).mapTo[AddedOneReviewResponse])
      } yield {
        ComposedReview(review, game, user)
      }

      createReviewFuture.value.pipeTo(sender())

    case updateReviewCommand: UpdateReview =>
      reviewManagerActor.forward(updateReviewCommand)

    case deleteReviewCommand @ DeleteReview(reviewId) =>
      val deletedReviewFuture = for {
        review <- EitherT(getComposedReview(reviewId))
        reviewWasDeleted <- EitherT((gameManagerActor ? deleteReviewCommand).
          mapTo[ReviewDeletedResponse]
        )
        _ <- EitherT((userManagerActor ? RemoveOneReview(review.author.userId))
          .mapTo[RemovedOneReviewResponse]
        )
      } yield {
        reviewWasDeleted
      }

      deletedReviewFuture.value.pipeTo(sender())

    case createCSVCommand: CreateReviewFromCSV =>
      reviewManagerActor.forward(createCSVCommand)
  }
}

package dev.galre.josue.akkaProject
package http

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

case class ReviewRouter(reviewManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.review.ReviewActor._

  private case class CreateReviewRequest(
    steamAppId:                 Long,
    authorId:                   Long,
    region:                     String,
    review:                     String,
    recommended:                Boolean,
    commentCount:               Option[Long],
    votesFunny:                 Option[Long],
    votesHelpful:               Option[Long],
    steamPurchase:              Boolean,
    receivedForFree:            Option[Boolean],
    writtenDuringEarlyAccess:   Boolean,
    authorPlaytimeForever:      Option[Double],
    authorPlaytimeLastTwoWeeks: Option[Double],
    authorPlaytimeAtReview:     Option[Double],
    authorLastPlayed:           Option[Double]
  ) {
    def toCommand: CreateReview = {
      val timestampCreated            = Option(System.currentTimeMillis())
      val timestampUpdated            = timestampCreated
      val weightedVoteScore           = Option(0D)
      val newRegion                   = Option(region)
      val newReview                   = Option(review)
      val newRecommended              = Option(recommended)
      val newSteamPurchase            = Option(steamPurchase)
      val newWrittenDuringEarlyAccess = Option(writtenDuringEarlyAccess)

      val reviewState = ReviewState(
        reviewId = 0,
        steamAppId = steamAppId,
        authorId = authorId,
        region = newRegion,
        timestampCreated = timestampCreated,
        timestampUpdated = timestampUpdated,
        review = newReview,
        recommended = newRecommended,
        votesHelpful = votesHelpful,
        votesFunny = votesFunny,
        weightedVoteScore = weightedVoteScore,
        commentCount = commentCount,
        steamPurchase = newSteamPurchase,
        receivedForFree = receivedForFree,
        writtenDuringEarlyAccess = newWrittenDuringEarlyAccess,
        authorPlaytimeForever = authorPlaytimeForever,
        authorPlaytimeLastTwoWeeks = authorPlaytimeLastTwoWeeks,
        authorPlaytimeAtReview = authorPlaytimeAtReview,
        authorLastPlayed = authorLastPlayed
      )

      CreateReview(reviewState)
    }
  }

  private case class UpdateReviewRequest(
    region:                     Option[String],
    review:                     Option[String],
    recommended:                Option[Boolean],
    votesHelpful:               Option[Long],
    votesFunny:                 Option[Long],
    commentCount:               Option[Long],
    receivedForFree:            Option[Boolean],
    authorPlaytimeForever:      Option[Double],
    authorPlaytimeLastTwoWeeks: Option[Double],
    authorPlaytimeAtReview:     Option[Double],
    authorLastPlayed:           Option[Double]
  ) {
    def toCommand(id: Long): UpdateReview = {
      val weightedVoteScore = Option(0D)

      UpdateReview(
        ReviewState(
          reviewId = id,
          region = region,
          review = review,
          recommended = recommended,
          votesHelpful = votesHelpful,
          votesFunny = votesFunny,
          weightedVoteScore = weightedVoteScore,
          commentCount = commentCount,
          receivedForFree = receivedForFree,
          authorPlaytimeForever = authorPlaytimeForever,
          authorPlaytimeLastTwoWeeks = authorPlaytimeLastTwoWeeks,
          authorPlaytimeAtReview = authorPlaytimeAtReview,
          authorLastPlayed = authorLastPlayed
        )
      )
    }
  }

  private def createReviewAction(createReview: CreateReviewRequest): Future[ReviewCreatedResponse] =
    (reviewManagerActor ? createReview.toCommand).mapTo[ReviewCreatedResponse]

  private def updateNameAction(id: Long, updateReview: UpdateReviewRequest): Future[ReviewUpdatedResponse] =
    (reviewManagerActor ? updateReview.toCommand(id)).mapTo[ReviewUpdatedResponse]

  private def getReviewInfoAction(id: Long): Future[GetReviewInfoResponse] =
    (reviewManagerActor ? GetReviewInfo(id)).mapTo[GetReviewInfoResponse]

  private def deleteReviewAction(id: Long): Future[ReviewDeletedResponse] =
    (reviewManagerActor ? DeleteReview(id)).mapTo[ReviewDeletedResponse]


  val routes: Route =
    pathPrefix("reviews") {
      concat(
        pathEndOrSingleSlash {

          post {
            entity(as[CreateReviewRequest]) { review =>
              onSuccess(createReviewAction(review)) {
                case ReviewCreatedResponse(Success(steamAppId)) =>
                  respondWithHeader(Location(s"/reviews/$steamAppId")) {
                    complete(StatusCodes.Created)
                  }

                case ReviewCreatedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          }
        },
        path(LongNumber) { steamAppId =>
          concat(
            get {
              onSuccess(getReviewInfoAction(steamAppId)) {
                case GetReviewInfoResponse(Success(state)) =>
                  complete(state)

                case GetReviewInfoResponse(Failure(exception)) =>
                  throw exception
              }
            },
            patch {
              entity(as[UpdateReviewRequest]) { updateName =>
                onSuccess(updateNameAction(steamAppId, updateName)) {
                  case ReviewUpdatedResponse(Success(state)) =>
                    complete(state)

                  case ReviewUpdatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            },
            delete {
              onSuccess(deleteReviewAction(steamAppId)) {
                case ReviewDeletedResponse(Success(_)) =>
                  complete(
                    Response(
                      statusCode = StatusCodes.OK.intValue,
                      message = Some("ReviewState was deleted successfully.")
                    )
                  )

                case ReviewDeletedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          )
        }
      )
    }
}

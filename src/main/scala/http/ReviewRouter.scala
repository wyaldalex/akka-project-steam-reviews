package dev.galre.josue.akkaProject
package http

import actors.game.GameActor.{ GetGameInfo, GetGameInfoResponse }
import actors.review.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByFilterResponse, GetAllReviewsByGame }
import actors.user.UserActor._

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

case class ReviewRouter(
  reviewManagerActor: ActorRef,
  userManagerActor:   ActorRef,
  gameManagerActor:   ActorRef
)
  (implicit timeout: Timeout, executionContext: ExecutionContext) extends Directives {

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

  private def addOneReviewAction(id: Long): Future[AddedOneReviewResponse] =
    (userManagerActor ? AddOneReview(id)).mapTo[AddedOneReviewResponse]

  private def deleteOneReviewAction(id: Long): Future[RemovedOneReviewResponse] =
    (userManagerActor ? RemoveOneReview(id)).mapTo[RemovedOneReviewResponse]

  private def updateNameAction(id: Long, updateReview: UpdateReviewRequest): Future[ReviewUpdatedResponse] =
    (reviewManagerActor ? updateReview.toCommand(id)).mapTo[ReviewUpdatedResponse]

  private def getReviewInfoAction(id: Long): Future[GetReviewInfoResponse] =
    (reviewManagerActor ? GetReviewInfo(id)).mapTo[GetReviewInfoResponse]

  private def deleteReviewAction(id: Long): Future[ReviewDeletedResponse] =
    (reviewManagerActor ? DeleteReview(id)).mapTo[ReviewDeletedResponse]

  private def getAllReviewsByUser(id: Long, page: Int, perPage: Int): Future[GetAllReviewsByFilterResponse] =
    (reviewManagerActor ? GetAllReviewsByAuthor(id, page, perPage)).mapTo[GetAllReviewsByFilterResponse]

  private def getAllReviewsByGame(id: Long, page: Int, perPage: Int): Future[GetAllReviewsByFilterResponse] =
    (reviewManagerActor ? GetAllReviewsByGame(id, page, perPage)).mapTo[GetAllReviewsByFilterResponse]

  private def checkIfAuthorIsValid(authorId: Long)(routeIfValid: Route): Route = {
    val userInfoResponse = (userManagerActor ? GetUserInfo(authorId)).mapTo[GetUserInfoResponse]

    onSuccess(
      userInfoResponse.map {
        case Right(_) =>
          Success(true)

        case Left(_) =>
          Failure(
            new IllegalArgumentException("The authorId entered is invalid, please select a valid user.")
          )
      }
    ) {
      case Success(_) =>
        routeIfValid

      case Failure(exception) =>
        throw exception
    }
  }


  private def checkIfGameIsValid(authorId: Long)(routeIfValid: Route): Route = {
    val gameInfoResponse = (gameManagerActor ? GetGameInfo(authorId)).mapTo[GetGameInfoResponse]

    onSuccess(
      gameInfoResponse.map {
        case Right(_) =>
          Success(true)

        case Left(_) =>
          Failure(
            new IllegalArgumentException("The steamAppId is invalid, please select a valid game.")
          )
      }
    ) {
      case Success(_) =>
        routeIfValid

      case Failure(exception) =>
        throw exception
    }
  }

  private def checkIfAuthorAndGameAreValid(steamAppId: Long, authorId: Long)(routeIfValid: Route): Route = {
    val gameInfoResponse = (gameManagerActor ? GetGameInfo(steamAppId)).mapTo[GetGameInfoResponse]
    val userInfoResponse = (userManagerActor ? GetUserInfo(authorId)).mapTo[GetUserInfoResponse]

    onSuccess {
      for {
        user <- userInfoResponse
        game <- gameInfoResponse
      } yield {
        (game, user) match {
          case (Right(_), Right(_)) =>
            Success(true)

          case (Left(_), _) =>
            Failure(
              new IllegalArgumentException("The authorId entered is invalid, please select a valid user.")
            )

          case (_, Left(_)) =>
            Failure(
              new IllegalArgumentException("The steamAppId is invalid, please select a valid game.")
            )

          case (_, _) =>
            Failure(
              new IllegalArgumentException("Both authorId and steamAppId are invalid, please check and try again.")
            )
        }
      }
    } {
      case Success(_) =>
        routeIfValid

      case Failure(exception) =>
        throw exception
    }
  }

  val routes: Route =
    pathPrefix("reviews") {
      concat(
        pathPrefix("filter") {
          get {
            concat(
              path("user" / LongNumber) { authorId =>
                paginationParameters { (page, perPage) =>

                  checkIfAuthorIsValid(authorId) {
                    onComplete(getAllReviewsByUser(authorId, page, perPage)) {
                      case Success(maybeContent) =>
                        maybeContent match {
                          case Right(allReviews) =>
                            complete(StatusCodes.OK, allReviews)

                          case Left(failure) =>
                            completeWithMessage(StatusCodes.BadRequest, Some(failure))
                        }

                      case Failure(_) =>
                        completeWithMessage(StatusCodes.BadRequest, None)
                    }
                  }
                }
              },
              path("game" / LongNumber) { steamAppId =>
                paginationParameters { (page, perPage) =>
                  checkIfGameIsValid(steamAppId) {
                    onComplete(getAllReviewsByGame(steamAppId, page, perPage)) {
                      case Success(maybeContent) =>
                        maybeContent match {
                          case Right(allReviews) =>
                            complete(allReviews)

                          case Left(exception) =>
                            completeWithMessage(StatusCodes.BadRequest, Some(exception))
                        }

                      case Failure(_) =>
                        completeWithMessage(StatusCodes.BadRequest, None)
                    }
                  }
                }
              }
            )
          }
        },
        path(LongNumber) { steamReviewId =>
          concat(
            get {
              onSuccess(getReviewInfoAction(steamReviewId)) {
                case Right(state) =>
                  complete(state)

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            },
            patch {
              entity(as[UpdateReviewRequest]) { updateName =>
                onSuccess(updateNameAction(steamReviewId, updateName)) {
                  case Right(state) =>
                    complete(state)

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              }
            },
            delete {
              onSuccess(getReviewInfoAction(steamReviewId)) {
                case Right(state) =>
                  val authorId = state.authorId

                  onSuccess(deleteReviewAction(steamReviewId)) {
                    case Right(_) =>

                      onSuccess(deleteOneReviewAction(authorId)) {
                        case Right(_) =>
                          completeWithMessage(StatusCodes.OK, Some("Review was deleted successfully."))

                        case Left(exception) =>
                          completeWithMessage(StatusCodes.BadRequest, Some(exception))
                      }

                    case Left(exception) =>
                      completeWithMessage(StatusCodes.BadRequest, Some(exception))
                  }

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            }
          )
        },
        pathEndOrSingleSlash {
          post {
            entity(as[CreateReviewRequest]) { review =>
              val authorId   = review.authorId
              val steamAppId = review.steamAppId

              checkIfAuthorAndGameAreValid(steamAppId, authorId) {
                onSuccess(createReviewAction(review)) {
                  case Right(steamReviewId) =>
                    onSuccess(addOneReviewAction(authorId)) {
                      case Right(_) =>
                        respondWithHeader(Location(s"/reviews/$steamReviewId")) {
                          complete(StatusCodes.Created)
                        }

                      case Left(exception) =>
                        completeWithMessage(StatusCodes.BadRequest, Some(exception))
                    }

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              }
            }
          }
        },
      )
    }
}

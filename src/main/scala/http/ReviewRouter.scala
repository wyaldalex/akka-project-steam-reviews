package dev.galre.josue.akkaProject
package http

import actors.game.GameActor.{ GetGameInfo, GetGameInfoResponse }
import actors.review.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByFilterResponse, GetAllReviewsByGame }
import actors.user.UserActor._

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directive, Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

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

  private def getAllReviewsByUser(id: Long, page: Int, perPage: Int): Future[Try[GetAllReviewsByFilterResponse]] =
    (reviewManagerActor ? GetAllReviewsByAuthor(id, page, perPage)).mapTo[Try[GetAllReviewsByFilterResponse]]

  private def getAllReviewsByGame(id: Long, page: Int, perPage: Int): Future[Try[GetAllReviewsByFilterResponse]] =
    (reviewManagerActor ? GetAllReviewsByGame(id, page, perPage)).mapTo[Try[GetAllReviewsByFilterResponse]]

  private def checkIfAuthorIsValid(authorId: Long)(routeIfValid: Route): Route = {
    val userInfoResponse = (userManagerActor ? GetUserInfo(authorId)).mapTo[GetUserInfoResponse]

    onSuccess(
      userInfoResponse.map {
        case GetUserInfoResponse(maybeAccount) =>
          maybeAccount match {
            case Success(_) =>
              Success(true)

            case Failure(_) =>
              Failure(
                new IllegalArgumentException("The authorId entered is invalid, please select a valid user.")
              )
          }
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
        case GetGameInfoResponse(maybeGame) =>
          maybeGame match {
            case Success(_) =>
              Success(true)

            case Failure(_) =>
              Failure(
                new IllegalArgumentException("The steamAppId is invalid, please select a valid game.")
              )
          }
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
          case (GetGameInfoResponse(maybeGame), GetUserInfoResponse(maybeAccount)) =>

            (maybeAccount, maybeGame) match {
              case (Success(_), Success(_)) =>
                Success(true)

              case (Failure(_), _) =>
                Failure(
                  new IllegalArgumentException("The authorId entered is invalid, please select a valid user.")
                )

              case (_, Failure(_)) =>
                Failure(
                  new IllegalArgumentException("The steamAppId is invalid, please select a valid game.")
                )

              case (_, _) =>
                Failure(
                  new IllegalArgumentException("Both authorId and steamAppId are invalid, please check and try again.")
                )
            }
        }
      }
    } {
      case Success(_) =>
        routeIfValid

      case Failure(exception) =>
        throw exception
    }
  }

  private def paginationParameters: Directive[(Int, Int)] =
    parameters("page".as[Int].withDefault(0), "perPage".as[Int].withDefault(50))

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
                      case Success(reviews) =>
                        complete(reviews)

                      case Failure(exception) =>
                        throw exception
                    }
                  }
                }
              },
              path("game" / LongNumber) { steamAppId =>
                paginationParameters { (page, perPage) =>
                  checkIfGameIsValid(steamAppId) {
                    onComplete(getAllReviewsByGame(steamAppId, page, perPage)) {
                      case Success(reviews) =>
                        complete(reviews)

                      case Failure(exception) =>
                        throw exception
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
                case GetReviewInfoResponse(Success(state)) =>
                  complete(state)

                case GetReviewInfoResponse(Failure(exception)) =>
                  throw exception
              }
            },
            patch {
              entity(as[UpdateReviewRequest]) { updateName =>
                onSuccess(updateNameAction(steamReviewId, updateName)) {
                  case ReviewUpdatedResponse(Success(state)) =>
                    complete(state)

                  case ReviewUpdatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            },
            delete {
              onSuccess(getReviewInfoAction(steamReviewId)) {
                case GetReviewInfoResponse(Success(state)) =>
                  val authorId = state.authorId

                  onSuccess(deleteReviewAction(steamReviewId)) {
                    case ReviewDeletedResponse(Success(_)) =>

                      onSuccess(deleteOneReviewAction(authorId)) {
                        case RemovedOneReviewResponse(Success(_)) =>
                          complete(
                            Response(
                              statusCode = StatusCodes.OK.intValue,
                              message = Some("Review was deleted successfully.")
                            )
                          )

                        case RemovedOneReviewResponse(Failure(exception)) =>
                          throw exception
                      }

                    case ReviewDeletedResponse(Failure(exception)) =>
                      throw exception
                  }

                case GetReviewInfoResponse(Failure(exception)) =>
                  throw exception
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
                  case ReviewCreatedResponse(Success(steamReviewId)) =>
                    onSuccess(addOneReviewAction(authorId)) {
                      case AddedOneReviewResponse(Success(_)) =>
                        respondWithHeader(Location(s"/reviews/$steamReviewId")) {
                          complete(StatusCodes.Created)
                        }

                      case AddedOneReviewResponse(Failure(exception)) =>
                        throw exception
                    }

                  case ReviewCreatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            }
          }
        },
      )
    }
}

package dev.galre.josue.steamreviews
package controller

import repository.entity.UserActor._

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.Future

final case class UserRouter(
  steamManagerWriter: ActorRef, steamManagerReader: ActorRef
)(implicit timeout: Timeout) extends Directives {


  private final case class CreateUserRequest(
    name: String,
    numGamesOwned: Option[Int],
    numReviews: Option[Int]
  ) {
    private val zero = 0

    def toCommand: CreateUser = {
      val newNumGamesOwned = if (numGamesOwned.isEmpty) Some(zero) else numGamesOwned
      val newNumReviews = if (numReviews.isEmpty) Some(zero) else numReviews

      CreateUser(name, newNumGamesOwned, newNumReviews)
    }
  }

  private final case class UpdateUserRequest(
    name: Option[String],
    numGamesOwned: Option[Int],
    numReviews: Option[Int]
  ) {
    def toCommand(id: Long): UpdateUser = UpdateUser(id, name, numGamesOwned, numReviews)
  }

  private def createUserAction(createUser: CreateUserRequest): Future[UserCreatedResponse] =
    (steamManagerWriter ? createUser.toCommand).mapTo[UserCreatedResponse]

  private def updateNameAction(id: Long, updateUser: UpdateUserRequest): Future[UserUpdatedResponse] =
    (steamManagerWriter ? updateUser.toCommand(id)).mapTo[UserUpdatedResponse]

  private def getUserInfoAction(id: Long): Future[GetUserInfoResponse] =
    (steamManagerReader ? GetUserInfo(id)).mapTo[GetUserInfoResponse]

  private def deleteUserAction(id: Long): Future[UserDeletedResponse] =
    (steamManagerWriter ? DeleteUser(id)).mapTo[UserDeletedResponse]


  val routes: Route =
    pathPrefix("users") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[CreateUserRequest]) {
              game =>
                onSuccess(createUserAction(game)) {
                  case Right(steamUserId) =>
                    respondWithHeader(Location(s"/users/$steamUserId")) {
                      complete(StatusCodes.Created)
                    }

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
            }
          }
        },
        path(LongNumber) {
          steamUserId =>
            concat(
              get {
                onSuccess(getUserInfoAction(steamUserId)) {
                  case Right(state) =>
                    complete(state)

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              },
              patch {
                entity(as[UpdateUserRequest]) {
                  updateUser =>
                    onSuccess(updateNameAction(steamUserId, updateUser)) {
                      case Right(state) =>
                        complete(state)

                      case Left(exception) =>
                        completeWithMessage(StatusCodes.BadRequest, Some(exception))
                    }
                }
              },
              delete {
                onSuccess(deleteUserAction(steamUserId)) {
                  case Right(_) =>
                    completeWithMessage(StatusCodes.OK, message = Some("UserState was deleted successfully."))

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              }
            )
        }
      )
    }
}

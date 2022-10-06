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

case class UserRouter(userManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.user.UserActor._

  private case class CreateUserRequest(name: String, numGamesOwned: Option[Int], numReviews: Option[Int]) {
    def toCommand: CreateUser = {
      val newNumGamesOwned = if (numGamesOwned.isEmpty) Some(0) else numGamesOwned
      val newNumReviews    = if (numReviews.isEmpty) Some(0) else numReviews

      CreateUser(name, newNumGamesOwned, newNumReviews)
    }
  }

  private case class UpdateUserRequest(name: Option[String], numGamesOwned: Option[Int], numReviews: Option[Int]) {
    def toCommand(id: Long): UpdateUser = UpdateUser(id, name, numGamesOwned, numReviews)
  }

  private def createUserAction(createUser: CreateUserRequest): Future[UserCreatedResponse] =
    (userManagerActor ? createUser.toCommand).mapTo[UserCreatedResponse]

  private def updateNameAction(id: Long, updateUser: UpdateUserRequest): Future[UserUpdatedResponse] =
    (userManagerActor ? updateUser.toCommand(id)).mapTo[UserUpdatedResponse]

  private def getUserInfoAction(id: Long): Future[GetUserInfoResponse] =
    (userManagerActor ? GetUserInfo(id)).mapTo[GetUserInfoResponse]

  private def deleteUserAction(id: Long): Future[UserDeletedResponse] =
    (userManagerActor ? DeleteUser(id)).mapTo[UserDeletedResponse]


  val routes: Route =
    pathPrefix("users") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[CreateUserRequest]) { game =>
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
        path(LongNumber) { steamUserId =>
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
              entity(as[UpdateUserRequest]) { updateUser =>
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

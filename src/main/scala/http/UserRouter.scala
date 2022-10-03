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

case class UserRouter(userManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.user.UserActor._

  private case class CreateUserRequest(name: String, numGamesOwned: Option[Long], numReviews: Option[Long]) {
    def toCommand: CreateUser = {
      val newNumGamesOwned = if (numGamesOwned.isEmpty) Some(0L) else numGamesOwned
      val newNumReviews    = if (numReviews.isEmpty) Some(0L) else numReviews

      CreateUser(name, newNumGamesOwned, newNumReviews)
    }
  }

  private case class UpdateUserRequest(name: Option[String], numGamesOwned: Option[Long], numReviews: Option[Long]) {
    def toCommand(id: BigInt): UpdateUser = UpdateUser(id, name, numGamesOwned, numReviews)
  }

  private def createUserAction(createUser: CreateUserRequest): Future[UserCreatedResponse] =
    (userManagerActor ? createUser.toCommand).mapTo[UserCreatedResponse]

  private def updateNameAction(id: BigInt, updateUser: UpdateUserRequest): Future[UserUpdatedResponse] =
    (userManagerActor ? updateUser.toCommand(id)).mapTo[UserUpdatedResponse]

  private def getUserInfoAction(id: BigInt): Future[GetUserInfoResponse] =
    (userManagerActor ? GetUserInfo(id)).mapTo[GetUserInfoResponse]

  private def deleteUserAction(id: BigInt): Future[UserDeletedResponse] =
    (userManagerActor ? DeleteUser(id)).mapTo[UserDeletedResponse]


  val routes: Route =
    pathPrefix("users") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[CreateUserRequest]) { game =>
              onSuccess(createUserAction(game)) {
                case UserCreatedResponse(Success(steamUserId)) =>
                  respondWithHeader(Location(s"/users/$steamUserId")) {
                    complete(StatusCodes.Created)
                  }

                case UserCreatedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          }
        },
        path(LongNumber) { steamUserId =>
          concat(
            get {
              onSuccess(getUserInfoAction(steamUserId)) {
                case GetUserInfoResponse(Success(state)) =>
                  complete(state)

                case GetUserInfoResponse(Failure(exception)) =>
                  throw exception
              }
            },
            patch {
              entity(as[UpdateUserRequest]) { updateName =>
                onSuccess(updateNameAction(steamUserId, updateName)) {
                  case UserUpdatedResponse(Success(state)) =>
                    complete(state)

                  case UserUpdatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            },
            delete {
              onSuccess(deleteUserAction(steamUserId)) {
                case UserDeletedResponse(Success(_)) =>
                  complete(
                    Response(
                      statusCode = StatusCodes.OK.intValue,
                      message = Some("UserState was deleted successfully.")
                    )
                  )

                case UserDeletedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          )
        }
      )
    }
}

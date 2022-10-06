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

case class GameRouter(gameManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.game.GameActor._

  private case class CreateGameRequest(steamAppName: String) {
    def toCommand: CreateGame = CreateGame(steamAppName)
  }

  private case class UpdateGameRequest(steamAppName: String) {
    def toCommand(id: Long): UpdateName = UpdateName(id, steamAppName)
  }

  private def createGameAction(createGame: CreateGameRequest): Future[GameCreatedResponse] =
    (gameManagerActor ? createGame.toCommand).mapTo[GameCreatedResponse]

  private def updateNameAction(id: Long, updateGame: UpdateGameRequest): Future[GameUpdatedResponse] =
    (gameManagerActor ? updateGame.toCommand(id)).mapTo[GameUpdatedResponse]

  private def getGameInfoAction(id: Long): Future[GetGameInfoResponse] =
    (gameManagerActor ? GetGameInfo(id)).mapTo[GetGameInfoResponse]

  private def deleteGameAction(id: Long): Future[GameDeletedResponse] =
    (gameManagerActor ? DeleteGame(id)).mapTo[GameDeletedResponse]


  val routes: Route =
    pathPrefix("games") {
      concat(
        pathEndOrSingleSlash {

          post {
            entity(as[CreateGameRequest]) { game =>
              onSuccess(createGameAction(game)) {
                case Right(steamAppId) =>
                  respondWithHeader(Location(s"/games/$steamAppId")) {
                    complete(StatusCodes.Created)
                  }

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            }
          }
        },
        path(LongNumber) { steamAppId =>
          concat(
            get {
              onSuccess(getGameInfoAction(steamAppId)) {
                case Right(state) =>
                  complete(state)

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            },
            patch {
              entity(as[UpdateGameRequest]) { updateName =>
                onSuccess(updateNameAction(steamAppId, updateName)) {
                  case Right(state) =>
                    complete(state)

                  case Left(exception) =>
                    completeWithMessage(StatusCodes.BadRequest, Some(exception))
                }
              }
            },
            delete {
              onSuccess(deleteGameAction(steamAppId)) {
                case Right(_) =>
                  completeWithMessage(StatusCodes.OK, Some("Game was deleted successfully."))

                case Left(exception) =>
                  completeWithMessage(StatusCodes.BadRequest, Some(exception))
              }
            }
          )
        }
      )
    }
}

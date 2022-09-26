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


case class GameRouter(gameManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.GameActor._

  case class CreateGameRequest(steamAppName: String) {
    def toCommand: CreateGame = CreateGame(steamAppName)
  }

  case class UpdateGameRequest(steamAppName: String) {
    def toCommand(id: BigInt): UpdateName = UpdateName(id, steamAppName)
  }

  def createGameAction(createGame: CreateGameRequest): Future[GameCreatedResponse] =
    (gameManagerActor ? createGame.toCommand).mapTo[GameCreatedResponse]

  def updateNameAction(id: BigInt, updateGame: UpdateGameRequest): Future[GameUpdatedResponse] =
    (gameManagerActor ? updateGame.toCommand(id)).mapTo[GameUpdatedResponse]

  def getGameInfoAction(id: BigInt): Future[GetGameInfoResponse] =
    (gameManagerActor ? GetGameInfo(id)).mapTo[GetGameInfoResponse]

  def deleteGameAction(id: BigInt): Future[GameDeletedResponse] =
    (gameManagerActor ? DeleteGame(id)).mapTo[GameDeletedResponse]


  val routes: Route =
    pathPrefix("games") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[CreateGameRequest]) { game =>
              onSuccess(createGameAction(game)) {
                case GameCreatedResponse(Success(steamAppId)) =>
                  respondWithHeader(Location(s"/games/$steamAppId")) {
                    complete(StatusCodes.Created)
                  }

                case GameCreatedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          }
        },
        path(LongNumber) { steamAppId =>
          concat(
            get {
              onSuccess(getGameInfoAction(steamAppId)) {
                case GetGameInfoResponse(Success(state)) =>
                  complete(state)

                case GetGameInfoResponse(Failure(exception)) =>
                  throw exception
              }
            },
            patch {
              entity(as[UpdateGameRequest]) { updateName =>
                onSuccess(updateNameAction(steamAppId, updateName)) {
                  case GameUpdatedResponse(Success(state)) =>
                    complete(state)

                  case GameUpdatedResponse(Failure(exception)) =>
                    throw exception
                }
              }
            },
            delete {
              onSuccess(deleteGameAction(steamAppId)) {
                case GameDeletedResponse(Success(_)) =>
                  complete("Game was deleted successfully.")

                case GameDeletedResponse(Failure(exception)) =>
                  throw exception
              }
            }
          )
        }
      )
    }
}

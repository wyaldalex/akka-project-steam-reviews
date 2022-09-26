package dev.galre.josue.akkaProject
package actors

import akka.actor.{ ActorLogging, Props }
import akka.persistence.PersistentActor

import scala.util.{ Failure, Success, Try }

object GameActor {
  // state
  case class GameState(steamAppId: BigInt, steamAppName: String)

  // commands
  case class CreateGame(steamAppName: String)

  case class UpdateName(id: BigInt, newName: String)

  case class DeleteGame(id: BigInt)

  case class GetGameInfo(id: BigInt)

  // events
  case class GameCreated(game: GameState)

  case class GameUpdated(newName: String)


  //responses
  case class GameCreatedResponse(steamAppId: Try[BigInt])

  case class GameUpdatedResponse(maybeGame: Try[GameState])

  case class GetGameInfoResponse(maybeGame: Try[GameState])

  case class GameDeletedResponse(gameWasDeletedSuccessfully: Try[Boolean])


  def props(userId: BigInt): Props = Props(new GameActor(userId))
}

class GameActor(steamAppId: BigInt)
  extends PersistentActor
  with ActorLogging {

  import GameActor._

  override def persistenceId: String = s"steam-appid-$steamAppId"

  var state: GameState = GameState(steamAppId, "")

  override def receiveCommand: Receive = {
    case CreateGame(name) =>
      val id = state.steamAppId

      persist(GameCreated(GameState(id, name))) { _ =>
        state = state.copy(steamAppName = name)
        sender() ! GameCreatedResponse(Success(id))
      }

    case UpdateName(_, newName) =>
      if (newName == state.steamAppName)
        sender() ! GameUpdatedResponse(
          Failure(new IllegalArgumentException("The new name cannot be equal to the previous one."))
        )
      else
        persist(GameUpdated(newName)) { _ =>
          state = state.copy(steamAppName = newName)

          sender() ! GameUpdatedResponse(Success(state))
        }

    case GetGameInfo(_) =>
      sender() ! GetGameInfoResponse(Success(state))

  }

  override def receiveRecover: Receive = {
    case GameCreated(game) =>
      state = game

    case GameUpdated(newName) =>
      state = state.copy(steamAppName = newName)
  }
}

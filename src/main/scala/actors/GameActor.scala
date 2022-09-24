package dev.galre.josue.akkaProject
package actors

import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{ Failure, Success, Try }

object GameActor {
  // state
  case class GameState(steamAppId: BigInt, steamAppName: String)

  // commands
  case class CreateGame(steamAppName: String)

  case class UpdateName(newName: String)

  case class DeleteGame(id: BigInt)

  case class GetGameInfo(id: BigInt)

  // events
  case class GameCreated(game: GameState)

  case class GameUpdated(newName: String)

  case object GameDeleted


  //responses
  case class GameCreatedResponse(steamAppId: BigInt)

  case class GameUpdatedResponse(maybeGame: Try[GameState])

  case class GetGameInfoResponse(maybeGame: Option[GameState])

  case class GameDeletedResponse(gameWasDeletedSuccessfully: Try[Boolean])


  def props(userId: BigInt): Props = Props(new GameActor(userId))
}

class GameActor(steamAppId: BigInt) extends PersistentActor {

  import GameActor._

  override def persistenceId: String = s"steam-appid-$steamAppId"

  var state: GameState = GameState(steamAppId, "")

  override def receiveCommand: Receive = {
    case CreateGame(name) =>
      val id = state.steamAppId

      persist(GameCreated(GameState(id, name))) { _ =>
        state = state.copy(steamAppName = name)
        sender() ! GameCreatedResponse(id)
      }

    case UpdateName(newName) =>
      if (newName == state.steamAppName)
        sender() ! GameUpdatedResponse(
          Failure(new IllegalArgumentException("The new name cannot be equal to the previous one."))
        )
      else
        persist(GameUpdated(newName)) { _ =>
          state = state.copy(steamAppName = newName)
          sender() ! GameUpdatedResponse(Success(state))
        }

    case GetGameInfo =>
      sender() ! GetGameInfoResponse(Some(state))

    case CreateGame(_) | UpdateName(_) | GetGameInfo =>
      sender() ! new IllegalAccessException("The selected account does not exists.")
  }

  override def receiveRecover: Receive = {
    case GameCreated(game) =>
      state = game

    case GameUpdated(newName) =>
      state = state.copy(steamAppName = newName)
  }
}

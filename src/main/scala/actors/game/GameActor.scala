package dev.galre.josue.akkaProject
package actors.game

import util.CborSerializable

import akka.actor.{ ActorLogging, Props }
import akka.persistence.PersistentActor

object GameActor {
  // state

  case class GameState(
    steamAppId:   Long,
    steamAppName: String
  )
    extends CborSerializable

  // commands
  case class CreateGame(steamAppName: String)

  case class UpdateName(id: Long, newName: String)

  case class DeleteGame(id: Long)

  case class GetGameInfo(id: Long)

  // events
  case class GameCreated(game: GameState) extends CborSerializable

  case class GameUpdated(newName: String) extends CborSerializable


  //responses
  type GameCreatedResponse = Either[String, Long]

  type GameUpdatedResponse = Either[String, GameState]

  type GetGameInfoResponse = Either[String, GameState]

  type GameDeletedResponse = Either[String, Boolean]


  def props(userId: Long): Props = Props(new GameActor(userId))
}

class GameActor(steamAppId: Long)
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
        sender() ! Right(id)
      }

    case UpdateName(_, newName) =>
      if (newName == state.steamAppName)
        sender() ! Left("The new name cannot be equal to the previous one.")

      else
        persist(GameUpdated(newName)) { _ =>
          state = state.copy(steamAppName = newName)

          sender() ! Right(state)
        }

    case GetGameInfo(_) =>
      sender() ! Right(state)

  }

  override def receiveRecover: Receive = {
    case GameCreated(game) =>
      state = game

    case GameUpdated(newName) =>
      state = state.copy(steamAppName = newName)
  }
}

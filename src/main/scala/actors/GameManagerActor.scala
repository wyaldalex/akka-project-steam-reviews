package dev.galre.josue.akkaProject
package actors

import akka.persistence.PersistentActor

import scala.collection.mutable
import scala.util.Success

object GameManagerActor {

  // games
  case class GameManager(
    var gameCount: BigInt = 0,
    games:         mutable.AnyRefMap[BigInt, ActorController]
    // TODO: Figure out how to check if a game with the same title already exists
  )

  // events
  case class GameActorCreated(id: BigInt)

  case class GameActorDeleted(id: BigInt)
}

class GameManagerActor extends PersistentActor {

  import GameActor._
  import GameManagerActor._

  var gameManagerState: GameManager = GameManager(games = mutable.AnyRefMap())

  override def persistenceId: String = "steam-games-manager"

  override def receiveCommand: Receive = {
    case command @ CreateGame(steamAppName) =>
      val steamGameId    = gameManagerState.gameCount
      val gameActorName  = s"steam-app-$steamGameId"
      val gameActor      = context.actorOf(
        GameActor.props(steamGameId),
        gameActorName
      )
      val controlledGame = ActorController(gameActor)

      persist(GameActorCreated(steamGameId)) { _ =>
        gameManagerState = gameManagerState.copy(
          gameCount = gameManagerState.gameCount + 1,
          games = gameManagerState.games.addOne(steamGameId -> controlledGame)
        )

        gameActor.forward(command)
      }

    case GetGameInfo(id) =>


    case DeleteGame(id) =>
      persist(GameActorDeleted(id)) { _ =>
        gameManagerState.games(id).isDisabled = true
        sender() ! GameDeletedResponse(Success(true))
      }

  }

  override def receiveRecover: Receive = {
    case GameActorCreated(id) =>
      val gameActorName = s"steam-app-$id"
      val gameActor     = context.child(id.toString)
        .getOrElse(
          context.actorOf(
            GameActor.props(id),
            gameActorName
          )
        )

      val controlledGame = ActorController(gameActor)

      gameManagerState = gameManagerState.copy(
        id,
        gameManagerState.games.addOne(id -> controlledGame)
      )

    case GameActorDeleted(id) =>
      gameManagerState.games(id).isDisabled = true
  }
}

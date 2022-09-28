package dev.galre.josue.akkaProject
package actors.game

import actors.GameController

import akka.actor.{ ActorLogging, Props }
import akka.pattern.{ ask, pipe }
import akka.persistence.PersistentActor
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

object GameManagerActor {

  // games
  case class GameManager(
    var gameCount: BigInt = 0,
    games:         mutable.AnyRefMap[BigInt, GameController]
  )

  // events
  case class GameActorCreated(id: BigInt, steamAppName: String)

  case class GameActorUpdated(id: BigInt, steamAppName: String)

  case class GameActorDeleted(id: BigInt)

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new GameManagerActor())
}

class GameManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import GameActor._
  import GameManagerActor._

  var gameManagerState: GameManager = GameManager(games = mutable.AnyRefMap())

  override def persistenceId: String = "steam-games-manager"

  def createActorName(steamGameId: BigInt): String = s"steam-app-$steamGameId"

  def gameAlreadyExists(steamAppName: String): Boolean =
    gameManagerState.games.values.exists(game => game.name == steamAppName && !game.isDisabled)

  def isGameAvailable(id: BigInt): Boolean =
    gameManagerState.games.contains(id) && !gameManagerState.games(id).isDisabled

  def notFoundExceptionCreator[T](id: BigInt): Try[T] =
    Failure(NotFoundException(s"A game with the id $id couldn't be found"))

  override def receiveCommand: Receive = {
    case createCommand @ CreateGame(steamAppName) =>
      if (gameAlreadyExists(steamAppName))
        sender() ! GameCreatedResponse(Failure(GameAlreadyExistsException("A game with this name already exists.")))
      else {
        val steamGameId    = gameManagerState.gameCount
        val gameActorName  = createActorName(steamGameId)
        val gameActor      = context.actorOf(
          GameActor.props(steamGameId),
          gameActorName
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamGameId, steamAppName)) { _ =>
          gameManagerState = gameManagerState.copy(
            gameCount = gameManagerState.gameCount + 1,
            games = gameManagerState.games.addOne(steamGameId -> controlledGame)
          )

          gameActor.forward(createCommand)
        }
      }

    case getCommand @ GetGameInfo(id) =>
      if (isGameAvailable(id))
        gameManagerState.games(id).actor.forward(getCommand)
      else
        sender() ! GetGameInfoResponse(notFoundExceptionCreator(id))

    case updateCommand @ UpdateName(id, newName) =>
      if (isGameAvailable(id))
        (gameManagerState.games(id).actor ? updateCommand).mapTo[GameUpdatedResponse].pipeTo(sender()).andThen {
          case Success(gameUpdatedResponse) => gameUpdatedResponse.maybeGame match {
            case Success(_) =>
              persist(GameActorUpdated(id, newName)) { _ =>
                gameManagerState.games(id).name = newName
              }

            case _ =>
          }

          case _ =>
        }
      else
        sender() ! GetGameInfoResponse(notFoundExceptionCreator(id))


    case DeleteGame(id) =>
      if (isGameAvailable(id))
        persist(GameActorDeleted(id)) { _ =>
          gameManagerState.games(id).isDisabled = true
          context.stop(gameManagerState.games(id).actor)

          sender() ! GameDeletedResponse(Success(true))
        }
      else
        sender() ! GameDeletedResponse(notFoundExceptionCreator(id))

  }

  override def receiveRecover: Receive = {
    case GameActorCreated(steamGameId, steamAppName) =>
      val gameActorName = createActorName(steamGameId)
      val gameActor     = context.child(gameActorName)
        .getOrElse(
          context.actorOf(
            GameActor.props(steamGameId),
            gameActorName
          )
        )

      val controlledGame = GameController(gameActor, steamAppName)

      gameManagerState = gameManagerState.copy(
        gameCount = steamGameId + 1,
        gameManagerState.games.addOne(steamGameId -> controlledGame)
      )

    case GameActorDeleted(id) =>
      gameManagerState.games(id).isDisabled = true

    case GameActorUpdated(id, name) =>
      gameManagerState.games(id).name = name
  }
}

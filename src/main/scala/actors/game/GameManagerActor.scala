package dev.galre.josue.akkaProject
package actors.game

import actors.GameController
import actors.game.GameActor.GameState

import akka.actor.{ ActorLogging, Props }
import akka.pattern.{ ask, pipe }
import akka.persistence._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

object GameManagerActor {

  // games
  case class GameManager(
    var gameCount: BigInt = BigInt(0),
    games:         mutable.AnyRefMap[BigInt, GameController]
  )

  val gameManagerSnapshotInterval = 10

  // commands
  case class CreateGameFromCSV(game: GameState)

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

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % gameManagerSnapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(gameManagerState)


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

          tryToSaveSnapshot()

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

                tryToSaveSnapshot()
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

          tryToSaveSnapshot()

          sender() ! GameDeletedResponse(Success(true))
        }
      else
        sender() ! GameDeletedResponse(notFoundExceptionCreator(id))

    case CreateGameFromCSV(GameState(steamAppId, steamAppName)) =>
      if (!gameManagerState.games.contains(steamAppId)) {
        val gameActor      = context.actorOf(
          GameActor.props(steamAppId),
          createActorName(steamAppId)
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamAppId, steamAppName)) { _ =>
          gameManagerState = gameManagerState.copy(
            games = gameManagerState.games.addOne(steamAppId -> controlledGame)
          )

          gameActor ! CreateGame(steamAppName)
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
    //      log.info(s"Got unhandled message: $any")

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

    case SnapshotOffer(_, state: GameManager) =>
      gameManagerState = state

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")
  }

}

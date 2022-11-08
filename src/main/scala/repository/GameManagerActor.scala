package dev.galre.josue.steamreviews
package repository

import repository.entity.GameActor
import repository.entity.GameActor._
import service.utils.{ Serializable, SnapshotSerializable }

import GameManagerActor._
import akka.actor.{ ActorLogging, Props }
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object GameManagerActor {

  // games
  final case class GameManager(
    var gameCount: Long = 0,
    games: mutable.HashMap[Long, GameController]
  ) extends Serializable

  val GameManagerSnapshotInterval = 10

  // commands
  final case class CreateGameFromCSV(game: GameState)

  // events
  final case class GameActorCreated(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long,
    steamAppName: String
  ) extends Serializable

  final case class GameActorUpdated(
    @JsonDeserialize(contentAs = classOf[Long]) id: Long,
    steamAppName: String
  ) extends Serializable

  final case class GameActorDeleted(id: Long) extends Serializable

  // snapshot

  final case class GameSave(
    @JsonDeserialize(contentAs = classOf[Long]) steamAppId: Long,
    @JsonDeserialize(contentAs = classOf[String]) steamAppName: String
  ) extends SnapshotSerializable

  final case class GameManagerSnapshotSave(
    @JsonDeserialize(contentAs = classOf[Long]) gameCount: Long,
    gameTupleList: List[GameSave]
  ) extends SnapshotSerializable

  def props(
    implicit timeout: Timeout,
    executionContext: ExecutionContext
  ): Props = Props(new GameManagerActor())
}

class GameManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {


  var gameManagerState: GameManager = GameManager(games = mutable.HashMap())

  override def persistenceId: String = "steam-games-manager"

  def createActorName(steamGameId: Long): String = "steam-app-" + steamGameId

  def gameAlreadyExists(steamAppName: String): Boolean =
    gameManagerState.games.values.exists(game => game.name == steamAppName && !game.isDisabled)

  def isGameAvailable(id: Long): Boolean =
    gameManagerState.games.contains(id) && !gameManagerState.games(id).isDisabled

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"A game with the id $id couldn't be found")

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % GameManagerSnapshotInterval == 0 && lastSequenceNr != 0) {
      val currentGames = gameManagerState.games
      val gamesList = currentGames.map(
        game => {
          val gameName = game._2.name
          GameSave(game._1, gameName)
        }
      ).toList
      val snapshotToSave = GameManagerSnapshotSave(gameManagerState.gameCount, gamesList)
      log.info(s"Creating snapshot with ${gamesList.size} entries on GameManagerActor")

      saveSnapshot(snapshotToSave)
    }


  override def receiveCommand: Receive = {
    case createCommand @ CreateGame(steamAppName) =>
      if (gameAlreadyExists(steamAppName)) {
        sender() ! Left("A game with this name already exists.")
      }
      else {
        val steamGameId = gameManagerState.gameCount
        val gameActorName = createActorName(steamGameId)
        val gameActor = context.actorOf(
          GameActor.props(steamGameId),
          gameActorName
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamGameId, steamAppName)) {
          _ =>
            gameManagerState = gameManagerState.copy(
              gameCount = gameManagerState.gameCount + 1,
              games = gameManagerState.games.addOne(steamGameId -> controlledGame)
            )

            tryToSaveSnapshot()

            gameActor.forward(createCommand)
        }
      }

    case getCommand @ GetGameInfo(id) =>
      if (isGameAvailable(id)) {
        gameManagerState.games(id).actor.forward(getCommand)
      } else {
        sender() ! notFoundExceptionCreator(id)
      }

    case updateCommand @ UpdateName(id, newName) =>
      if (isGameAvailable(id)) {
        val replyTo = sender()
        (gameManagerState.games(id).actor ? updateCommand)
          .mapTo[GameUpdatedResponse]
          .onComplete {
            case Success(response) =>
              response match {
                case right: Right[String, GameState] =>
                  persist(GameActorUpdated(id, newName)) {
                    event =>
                      gameManagerState.games(id).name = event.steamAppName

                      tryToSaveSnapshot()
                      replyTo ! right
                  }

                case left: Left[String, GameState] =>
                  replyTo ! left
              }

            case Failure(_) =>
              replyTo ! Left("Failed to update the selected game")
          }
      } else {
        sender() ! notFoundExceptionCreator(id)
      }

    case DeleteGame(id) =>
      if (isGameAvailable(id)) {
        persist(GameActorDeleted(id)) {
          _ =>
            gameManagerState.games(id).isDisabled = true
            context.stop(gameManagerState.games(id).actor)

            tryToSaveSnapshot()

            sender() ! Right(value = true)
        }
      } else {
        sender() ! notFoundExceptionCreator(id)
      }

    case CreateGameFromCSV(GameState(steamAppId, steamAppName)) =>
      if (!gameManagerState.games.contains(steamAppId)) {
        val gameActor = context.actorOf(
          GameActor.props(steamAppId),
          createActorName(steamAppId)
        )
        val controlledGame = GameController(gameActor, steamAppName)

        persist(GameActorCreated(steamAppId, steamAppName)) {
          _ =>
            gameManagerState = gameManagerState.copy(
              games = gameManagerState.games.addOne(steamAppId -> controlledGame)
            )

            gameActor ! CreateGame(steamAppName)
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(getSavedSnapshotMessage("GameManagerActor", metadata))

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(getFailedSnapshotMessage("GameManagerActor", metadata, reason))
      reason.printStackTrace()


    case any: Any =>

  }

  def createGameFromRecover(steamGameId: Long, steamAppName: String): GameController = {
    val gameActorName = createActorName(steamGameId)
    val gameActor = context.child(gameActorName)
      .getOrElse(
        context.actorOf(
          GameActor.props(steamGameId),
          gameActorName
        )
      )

    GameController(gameActor, steamAppName)
  }

  override def receiveRecover: Receive = {
    case GameActorCreated(steamGameId, steamAppName) =>
      val controlledGame = createGameFromRecover(steamGameId, steamAppName)

      gameManagerState = gameManagerState.copy(
        gameCount = steamGameId + 1,
        gameManagerState.games.addOne(steamGameId -> controlledGame)
      )

    case GameActorDeleted(id) =>
      gameManagerState.games(id).isDisabled = true

    case GameActorUpdated(id, name) =>
      gameManagerState.games(id).name = name

    case SnapshotOffer(metadata, GameManagerSnapshotSave(gameCount, gameTupleList)) =>
      log.info(s"Recovered game snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
      gameManagerState = gameManagerState.copy(gameCount = gameCount)

      gameTupleList.foreach {
        case GameSave(steamAppId, steamAppName) =>
          val controlledGame = createGameFromRecover(steamAppId, steamAppName)

          gameManagerState = gameManagerState.copy(
            games = gameManagerState.games.addOne(steamAppId -> controlledGame)
          )
      }

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")
  }

}

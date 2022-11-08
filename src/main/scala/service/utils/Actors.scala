package dev.galre.josue.steamreviews
package service.utils

import repository.{ GameManagerActor, ReviewManagerActor, UserManagerActor }
import service.command.{ GameCommand, ReviewCommand, UserCommand }
import service.query.{ GameQuery, ReviewQuery, UserQuery }

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object Actors {

  final case class StateManagers(
    private val gameCommand: ActorRef,
    private val gameQuery: ActorRef,
    private val reviewCommand: ActorRef,
    private val reviewQuery: ActorRef,
    private val userCommand: ActorRef,
    private val userQuery: ActorRef,
    private val csvLoaderActor: ActorRef,
  ) {
    object Command {
      val game: ActorRef = gameCommand
      val review: ActorRef = reviewCommand
      val user: ActorRef = userCommand
      val csvLoader: ActorRef = csvLoaderActor
    }

    object Query {
      val game: ActorRef = gameQuery
      val review: ActorRef = reviewQuery
      val user: ActorRef = userQuery
    }

  }

  def init()(
    implicit system: ActorSystem,
    dispatcher: ExecutionContext,
    timeout: Timeout
  ): StateManagers = {

    val gameManagerActor = system.actorOf(
      GameManagerActor.props,
      "steam-game-manager"
    )
    val userManagerActor = system.actorOf(
      UserManagerActor.props,
      "steam-user-manager"
    )
    val reviewManagerActor = system.actorOf(
      ReviewManagerActor.props,
      "steam-review-manager"
    )

    val gameCommand = system.actorOf(
      GameCommand.props(gameManagerActor),
      "game-command"
    )
    val reviewCommand = system.actorOf(
      ReviewCommand.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "review-command"
    )
    val userCommand = system.actorOf(
      UserCommand.props(userManagerActor),
      "user-command"
    )

    val gameQuery = system.actorOf(
      GameQuery.props(gameManagerActor),
      "game-query"
    )
    val reviewQuery = system.actorOf(
      ReviewQuery.props(reviewManagerActor),
      "review-query"
    )
    val userQuery = system.actorOf(
      UserQuery.props(userManagerActor),
      "user-query"
    )

    val csvLoaderActor = system.actorOf(
      CSVLoaderActor.props(gameCommand, reviewCommand, userCommand),
      "json-loader"
    )

    StateManagers(
      gameCommand,
      gameQuery,
      reviewCommand,
      reviewQuery,
      userCommand,
      userQuery,
      csvLoaderActor
    )
  }
}

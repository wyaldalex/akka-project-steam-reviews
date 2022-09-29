package dev.galre.josue.akkaProject
package app

import actors.game.GameManagerActor
import actors.review.ReviewManagerActor
import actors.user.UserManagerActor

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object MainApp {
  def initAkkaActors()(
    implicit
    system:     ActorSystem,
    dispatcher: ExecutionContext,
    timeout:    Timeout
  ):
  (ActorRef, ActorRef, ActorRef) = {

    val gameManagerActor   = system.actorOf(
      GameManagerActor.props,
      "steam-game-manager"
    )
    val userManagerActor   = system.actorOf(
      UserManagerActor.props,
      "steam-user-manager"
    )
    val reviewManagerActor = system.actorOf(
      ReviewManagerActor.props,
      "steam-review-manager"
    )

    (gameManagerActor, userManagerActor, reviewManagerActor)
  }

}

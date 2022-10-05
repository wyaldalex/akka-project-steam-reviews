package dev.galre.josue.akkaProject

import akka.actor.ActorRef

package object actors {
  case class GameController(actor: ActorRef, var name: String, var isDisabled: Boolean = false)

  case class UserController(actor: ActorRef, var isDisabled: Boolean = false)

  case class ReviewController(actor: ActorRef, userId: Long, steamAppId: Long, var isDisabled: Boolean = false)

}

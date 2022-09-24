package dev.galre.josue.akkaProject

import akka.actor.ActorRef

package object actors {
  case class ActorController(actor: ActorRef, var isDisabled: Boolean = false)

}

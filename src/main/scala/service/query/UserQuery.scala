package dev.galre.josue.steamreviews
package service.query

import repository.entity.UserActor.GetUserInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object UserQuery {
  def props(userManagerActor: ActorRef): Props
  = Props(new UserQuery(userManagerActor))
}

class UserQuery(userManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getUserCommand: GetUserInfo =>
      userManagerActor.forward(getUserCommand)
  }
}

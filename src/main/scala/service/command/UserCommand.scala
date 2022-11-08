package dev.galre.josue.steamreviews
package service.command

import repository.UserManagerActor.CreateUserFromCSV
import repository.entity.UserActor.{ CreateUser, DeleteUser, UpdateUser }

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object UserCommand {
  def props(userManagerActor: ActorRef): Props
  = Props(new UserCommand(userManagerActor))
}

class UserCommand(userManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    // All user messages
    case createUserCommand: CreateUser =>
      userManagerActor.forward(createUserCommand)

    case updateUserCommand: UpdateUser =>
      userManagerActor.forward(updateUserCommand)

    case deleteUserCommand: DeleteUser =>
      userManagerActor.forward(deleteUserCommand)

    case createCSVCommand: CreateUserFromCSV =>
      userManagerActor.forward(createCSVCommand)

  }
}

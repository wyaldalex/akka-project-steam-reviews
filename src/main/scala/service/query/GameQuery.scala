package dev.galre.josue.steamreviews
package service.query

import repository.entity.GameActor.GetGameInfo

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }

object GameQuery {
  def props(gameManagerActor: ActorRef): Props
  = Props(new GameQuery(gameManagerActor))
}

class GameQuery(gameManagerActor: ActorRef)
  extends Actor
  with ActorLogging {

  override def receive: Receive = {

    case getGameCommand: GetGameInfo =>
      gameManagerActor.forward(getGameCommand)
  }
}

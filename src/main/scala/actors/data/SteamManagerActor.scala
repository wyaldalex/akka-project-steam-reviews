package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.review.ReviewActor.Review
import actors.user.UserActor.User

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.Timeout

object SteamManagerActor {

  case object InitCSVLoadToManagers

  case object FinishCSVLoad

  case class CSVLoadFailure(exception: Throwable)

  case class CSVDataToLoad(
    review: Review,
    user:   User,
    game:   GameState
  )

  def props(
    gameManagerActor: ActorRef,
    userManagerActor: ActorRef, reviewManagerActor: ActorRef
  )
    (implicit timeout: Timeout): Props =
    Props(
      new SteamManagerActor(
        gameManagerActor, userManagerActor, reviewManagerActor
      )
    )
}

class SteamManagerActor(
  gameManagerActor: ActorRef, userManagerActor: ActorRef, reviewManagerActor: ActorRef
)(implicit timeout: Timeout)
  extends Actor
  with ActorLogging {

  import actors.InitCSVLoad

  import SteamManagerActor._

  override def receive: Receive = {
    case InitCSVLoadToManagers =>
      gameManagerActor ! InitCSVLoad
      userManagerActor ! InitCSVLoad
      reviewManagerActor ! InitCSVLoad

    case FinishCSVLoad =>

    case CSVLoadFailure =>

  }
}

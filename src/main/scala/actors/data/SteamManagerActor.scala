package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.game.GameManagerActor.CreateGameFromCSV
import actors.review.ReviewActor.ReviewState
import actors.review.ReviewManagerActor.CreateReviewFromCSV
import actors.user.UserActor.UserState
import actors.user.UserManagerActor.CreateUserFromCSV

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.Timeout

object SteamManagerActor {

  case object InitCSVLoadToManagers

  case object FinishCSVLoadToManagers

  case object Ack

  case class CSVLoadFailure(exception: Throwable)

  case class CSVDataToLoad(
    review: ReviewState,
    user:   UserState,
    game:   GameState
  )

  def props(
    gameManagerActor:   ActorRef,
    userManagerActor:   ActorRef,
    reviewManagerActor: ActorRef
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

  import SteamManagerActor._

  override def receive: Receive = {
    case InitCSVLoadToManagers =>
      log.info("Initialized CSV Data load.")
      sender() ! Ack

    case CSVDataToLoad(review, user, game) =>
      log.info(s"Received CSV Data for review ${review.reviewId}")
      gameManagerActor ! CreateGameFromCSV(game)
      userManagerActor ! CreateUserFromCSV(user)
      reviewManagerActor ! CreateReviewFromCSV(review)
      sender() ! Ack

    case FinishCSVLoadToManagers =>
      log.info("Finished successfully CSV Load")

    case CSVLoadFailure(exception) =>
      log.error(
        s"CSV Load failed due to ${exception.getMessage}.\nStack trace: ${
          exception
            .printStackTrace()
        }\nexception: ${exception.toString}"
      )
  }
}

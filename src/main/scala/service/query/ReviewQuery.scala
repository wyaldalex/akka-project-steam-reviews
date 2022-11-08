package dev.galre.josue.steamreviews
package service.query

import repository.ReviewManagerActor.{ GetAllReviewsByAuthor, GetAllReviewsByGame, ReviewsByFilterContent }
import repository.entity.ReviewActor.{ GetReviewInfo, ReviewState }

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object ReviewQuery {
  def props(reviewManagerActor: ActorRef)
    (implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new
      ReviewQuery(reviewManagerActor)
  )
}

class ReviewQuery(reviewManagerActor: ActorRef)
  (implicit timeout: Timeout, executionContext: ExecutionContext)
  extends Actor
  with ActorLogging {

  def askReviewsByFilter(command: Any, perPage: Int, replyTo: ActorRef): Unit = {
    (reviewManagerActor ? command)
      .mapTo[Iterable[Option[ReviewState]]]
      .onComplete {
        case Success(value) =>
          replyTo ! Right(ReviewsByFilterContent(perPage, value.toList))

        case Failure(_) =>
          replyTo ! Left(
            "There was a failure while trying to extract all the reviews of this game, please try again later."
          )
      }
  }

  override def receive: Receive = {

    case getReviewCommand: GetReviewInfo =>
      reviewManagerActor.forward(getReviewCommand)

    case getAllReviewsByUserCommand @ GetAllReviewsByAuthor(_, _, perPage) =>
      askReviewsByFilter(getAllReviewsByUserCommand, perPage, sender())

    case getAllReviewsByGameCommand @ GetAllReviewsByGame(_, _, perPage) =>
      askReviewsByFilter(getAllReviewsByGameCommand, perPage, sender())
  }
}

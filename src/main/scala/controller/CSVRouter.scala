package dev.galre.josue.steamreviews
package controller

import service.utils.CSVLoaderActor

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.{ Failure, Success }

final case class CSVRouter(csvLoaderActor: ActorRef)(
  implicit timeout: Timeout
) {

  private val csvFile = "src/main/resources/steam_reviews.csv"

  private def startCSVLoadAction(quantity: Int, startPosition: Int): Future[String] =
    (csvLoaderActor ? CSVLoaderActor.LoadCSV(
      csvFile,
      numberOfElements = quantity,
      startPosition = startPosition
    )).mapTo[String]

  val routes: Route =
    pathPrefix("csv") {
      path("load") {
        get {
          parameters(
            Symbol("quantity").withDefault(Int.MaxValue),
            Symbol("startPosition").withDefault(default = 0)
          ) {
            (quantity, startPosition) =>
              onComplete(startCSVLoadAction(quantity, startPosition)) {
                case Failure(exception) =>
                  completeWithMessage(
                    StatusCodes.BadRequest,
                    Some(s"Failed to load CSV due to: ${exception.getMessage}")
                  )

                case Success(message) =>
                  completeWithMessage(StatusCodes.OK, Some(message))
              }
          }
        }
      }
    }

}

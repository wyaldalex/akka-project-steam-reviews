package dev.galre.josue.akkaProject
package http

import swagger.SwaggerDocService

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import java.io.FileNotFoundException

object MainRouter {

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case _: FileNotFoundException => complete(
      StatusCodes.NotFound, Response(
        statusCode = StatusCodes.NotFound.intValue, message = Some("File not found")
      )
    )

    case NotFoundException(message) =>
      complete(
        StatusCodes.NotFound,
        Response(statusCode = StatusCodes.NotFound.intValue, message = Some(message))
      )

    case AlreadyExistsException(message) =>
      complete(
        StatusCodes.BadRequest,
        Response(statusCode = StatusCodes.BadRequest.intValue, message = Some(message))
      )

    case GameAlreadyExistsException(message) =>
      complete(
        StatusCodes.BadRequest,
        Response(statusCode = StatusCodes.BadRequest.intValue, message = Some(message))
      )

    case exception: IllegalArgumentException =>
      complete(
        StatusCodes.BadRequest,
        Response(statusCode = StatusCodes.BadRequest.intValue, message = Some(exception.getMessage))
      )

    case exception: RuntimeException =>
      complete(
        StatusCodes.BadRequest,
        Response(statusCode = StatusCodes.BadRequest.intValue, message = Some(exception.getMessage))
      )
  }

  def apply(gameManagerActor: ActorRef, userManagerActor: ActorRef)(implicit timeout: Timeout): Route = {
    pathPrefix("api") {
      concat(
        handleExceptions(exceptionHandler) {
          concat(
            GameRouter(gameManagerActor).routes,
            UserRouter(userManagerActor).routes
          )
        },
        SwaggerDocService.routes
      )
    }
  }

}

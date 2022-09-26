package dev.galre.josue.akkaProject
package http

import swagger.SwaggerDocService

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import akka.util.Timeout

import java.io.FileNotFoundException

object MainRouter {

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case _: FileNotFoundException => complete(StatusCodes.NotFound, "File not found.")
    case NotFoundException(message) => complete(StatusCodes.NotFound, message)
    case AlreadyExistsException(message) => complete(StatusCodes.BadRequest, message)
    case GameAlreadyExistsException(message) => complete(StatusCodes.BadRequest, message)
    case exception: IllegalArgumentException => complete(StatusCodes.BadRequest, exception.getMessage)
  }

  def apply(gameManagerActor: ActorRef)(implicit timeout: Timeout): Route = {
    pathPrefix("api") {
      concat(
        handleExceptions(exceptionHandler) {
          concat(
            GameRouter(gameManagerActor).routes
          )
        },
        SwaggerDocService.routes
      )
    }
  }

}

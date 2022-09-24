package dev.galre.josue.akkaProject
package http

import swagger.SwaggerDocService

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import akka.util.Timeout

import java.io.FileNotFoundException

object MainRouter {

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case _: FileNotFoundException => complete(404, "File not found.")
    case NotFoundException(message) => complete(404, message)
    case AlreadyExistsException(message) => complete(400, message)
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

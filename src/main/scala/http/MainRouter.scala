package dev.galre.josue.akkaProject
package http

import swagger.SwaggerDocService

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.ExecutionContext

object MainRouter {

  def apply(
    gameManagerActor:   ActorRef,
    userManagerActor:   ActorRef,
    reviewManagerActor: ActorRef
  )
    (implicit timeout: Timeout, executionContext: ExecutionContext): Route = {
    pathPrefix("api") {
      concat(
        concat(
          GameRouter(gameManagerActor).routes,
          UserRouter(userManagerActor).routes,
          ReviewRouter(reviewManagerActor, userManagerActor, gameManagerActor).routes
        ),
        SwaggerDocService.routes
      )
    }
  }

}

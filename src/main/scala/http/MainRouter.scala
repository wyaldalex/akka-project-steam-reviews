package dev.galre.josue.akkaProject
package http

import swagger.SwaggerDocService

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object MainRouter {

  def apply(): Route = {
    concat(
      SwaggerDocService.routes
    )
  }

}

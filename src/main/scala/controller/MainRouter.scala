package dev.galre.josue.steamreviews
package controller

import service.utils.Actors.StateManagers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

final case class MainRouter(
  stateManagers: StateManagers
)(implicit timeout: Timeout) {

  val routes: Route =
    pathPrefix("api") {
      concat(
        GameRouter(stateManagers.Command.game, stateManagers.Query.game).routes,
        UserRouter(stateManagers.Command.user, stateManagers.Query.user).routes,
        ReviewRouter(stateManagers.Command.review, stateManagers.Query.review).routes,
        CSVRouter(stateManagers.Command.csvLoader).routes,
      )
    }

}

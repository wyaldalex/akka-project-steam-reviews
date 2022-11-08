package dev.galre.josue.steamreviews

import controller.MainRouter
import service.utils.{ Actors, Binder, Config }

import akka.http.scaladsl.Http

object Application {
  // TODO: Implement CQRS
  // (Sebastian suggested to wait until module 9 to implement this, along with other techniques)
  def main(args: Array[String]): Unit = {
    val config = Config()

    import config.Implicits._

    val stateManagers = Actors.init
    val router = MainRouter(stateManagers)

    Binder(Http().newServerAt(config.address, config.port).bind(router.routes))
  }
}


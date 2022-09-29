package dev.galre.josue.akkaProject
package app

import actors.data.{ CSVLoaderActor, SteamManagerActor }
import actors.game.GameManagerActor
import actors.review.ReviewManagerActor
import actors.user.UserManagerActor
import http.MainRouter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object MainApp {
  def startHttpServer()(implicit system: ActorSystem): Unit = {
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout   : Timeout          = Timeout(5.seconds)

    val gameManagerActor   = system.actorOf(
      GameManagerActor.props,
      "steam-game-manager"
    )
    val userManagerActor   = system.actorOf(
      UserManagerActor.props,
      "steam-user-manager"
    )
    val reviewManagerActor = system.actorOf(
      ReviewManagerActor.props,
      "steam-review-manager"
    )
    val steamManagerActor  = system.actorOf(
      SteamManagerActor.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "steam-manager"
    )
    val csvLoaderActor     = system.actorOf(
      CSVLoaderActor.props(steamManagerActor),
      "json-loader"
    )

    csvLoaderActor ! CSVLoaderActor.LoadCSV("src/main/resources/steam_reviews.csv")

    val routes = MainRouter(gameManagerActor, userManagerActor, reviewManagerActor)

    val boundServer = Http().newServerAt("localhost", 8080).bind(routes)

    boundServer.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server started at: http://${address.getAddress}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bind server due to: $exception")
        system.terminate()
    }

  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("AkkaProjectSystem")

    startHttpServer()
  }
}

package dev.galre.josue.akkaProject
package app

import actors.GameManagerActor
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

    val gameManagerActor = system.actorOf(GameManagerActor.props, "steam-game-manager")

    val routes = MainRouter(gameManagerActor)

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

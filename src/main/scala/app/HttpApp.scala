package dev.galre.josue.akkaProject
package app

import app.MainApp.initAkkaActors
import http.MainRouter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object HttpApp {

  def main(args: Array[String]): Unit = {
    implicit val system    : ActorSystem      = ActorSystem("SteamReviewsMicroservice")
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout   : Timeout          = Timeout(20.seconds)

    val (gameManagerActor, userManagerActor, reviewManagerActor) = initAkkaActors()

    val routes = MainRouter(gameManagerActor, userManagerActor, reviewManagerActor)

    val boundServer = Http().newServerAt("0.0.0.0", 8080).bind(routes)

    boundServer.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server started at: http://${address.getAddress}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bind server due to: $exception")
        system.terminate()
    }
  }
}

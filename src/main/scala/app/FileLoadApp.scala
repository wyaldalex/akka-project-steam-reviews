package dev.galre.josue.akkaProject
package app

import actors.data.{ CSVLoaderActor, SteamManagerActor }
import app.MainApp.initAkkaActors

import akka.actor.ActorSystem
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object FileLoadApp {

  def main(args: Array[String]): Unit = {
    implicit val system    : ActorSystem      = ActorSystem("SteamReviewsMicroservice")
    implicit val timeout   : Timeout          = Timeout(20.seconds)
    implicit val dispatcher: ExecutionContext = system.dispatcher

    val (gameManagerActor, userManagerActor, reviewManagerActor) = initAkkaActors()

    val steamManagerActor = system.actorOf(
      SteamManagerActor.props(gameManagerActor, userManagerActor, reviewManagerActor),
      "steam-manager"
    )
    val csvLoaderActor    = system.actorOf(
      CSVLoaderActor.props(steamManagerActor),
      "json-loader"
    )

    csvLoaderActor ! CSVLoaderActor.LoadCSV("src/main/resources/steam_reviews.csv", numberOfElements = 10)
  }

}

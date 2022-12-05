package dev.galre.josue.steamreviews
package service.utils

import akka.actor.ActorRef
import akka.util.Timeout
import dev.galre.josue.steamreviews.service.utils.Actors.StateManagers
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class CSVLoaderActorTest extends GherkinSpec {

  Feature("A CSVLoaderActorTest behavior"){

    implicit def executionContext: ExecutionContext = system.dispatcher
    implicit def timeOut: Timeout = Timeout(20.seconds)

    val stateManagers: StateManagers = Actors.init

    val filePath = "src/test/resources/steam_reviews.csv"
    val filePathErrors = "src/test/resources/steam_reviews_errata.csv"


    val csvLoaderActor = system.actorOf(CSVLoaderActor.props(
      stateManagers.Command.game,
      stateManagers.Command.review, stateManagers.Command.user
    ))

    Scenario("A CSVLoaderActorTest should load from csv file") {
      Given("A CSV file")
      When("A LoadCSV command is sent")
      csvLoaderActor ! CSVLoaderActor.LoadCSV(
        filePath,
        0,
        10
      )

      Then("We should be able to access the loaded values")
      val result = expectMsgClass(classOf[String])
      info(s"Loader response $result")
    }

    Scenario("A CSVLoaderActorTest should fail to load from non existent csv file") {
      Given("A CSV file")
      When("A LoadCSV command is sent")
      csvLoaderActor ! CSVLoaderActor.LoadCSV(
        "",
        0,
        10
      )

      Then("We should be able to access the loaded values")
      val result = expectMsgClass(classOf[String])
      info(s"Loader response $result")
    }

    Scenario("A CSVLoaderActorTest should fail to load with a malformed csv file") {
      Given("A CSV file")
      When("A LoadCSV command is sent")
      csvLoaderActor ! CSVLoaderActor.LoadCSV(
        filePathErrors,
        0,
        10
      )

      Then("We should be able to access the loaded values")
      val result = expectMsgClass(classOf[String])
      info(s"Loader response malformed csv: $result")
    }

  }

}

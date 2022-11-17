package dev.galre.josue.steamreviews
package service.utils

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import dev.galre.josue.steamreviews.service.utils.Actors.StateManagers
import dev.galre.josue.steamreviews.spec.GherkinSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ActorsTest extends GherkinSpec {

  Feature("An Actor class behavior") {

    Scenario("An Actor class should return a StateManagers on init method") {

      Given("implicit ActorSystem, ExecutionContext and Timeout")

      //implicit val actorSystem: ActorSystem =
      implicit val system = ActorSystem()
      //implicit def actorSystem: ActorSystem = ActorSystem("adas")

      implicit def executionContext: ExecutionContext = system.dispatcher

      implicit def timeOut: Timeout = Timeout(5.seconds)
      val stateManagers: StateManagers = Actors.init

      assert(stateManagers.Command.game.isInstanceOf[ActorRef])
    }

  }

}

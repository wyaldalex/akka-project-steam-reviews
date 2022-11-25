package dev.galre.josue.steamreviews
package service.utils

import akka.actor.ActorRef
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
     // implicit val system = ActorSystem()
      //implicit def actorSystem: ActorSystem = ActorSystem("adas")

      implicit def executionContext: ExecutionContext = system.dispatcher
      implicit def timeOut: Timeout = Timeout(5.seconds)
      When("Actors.init is called")
      val stateManagers: StateManagers = Actors.init
      Then("StateManagers should be initialized properly with ActorRefs")
      assert(stateManagers.Command.game.isInstanceOf[ActorRef])
      assert(stateManagers.Query.game.isInstanceOf[ActorRef])
      assert(stateManagers.Command.user.isInstanceOf[ActorRef])
      assert(stateManagers.Query.user.isInstanceOf[ActorRef])
      assert(stateManagers.Command.review.isInstanceOf[ActorRef])
      assert(stateManagers.Query.review.isInstanceOf[ActorRef])
      assert(stateManagers.Command.csvLoader.isInstanceOf[ActorRef])

    }

  }

}

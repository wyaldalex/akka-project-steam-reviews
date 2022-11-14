package dev.galre.josue.steamreviews
package spec

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, Inside, Inspectors, OptionValues }
import org.scalatest.flatspec.FixtureAnyFlatSpecLike
import org.scalatest.matchers.must.Matchers

abstract class UnitSpec
  extends TestKit(ActorSystem("SteamTestActorSystem"))
  with FixtureAnyFlatSpecLike
  with Matchers
  with OptionValues
  with Inside
  with Inspectors
  with BeforeAndAfterAll
  with ImplicitSender {
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

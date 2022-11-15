package dev.galre.josue.steamreviews
package spec

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, GivenWhenThen, Inside, Inspectors, OptionValues }
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.matchers.must.Matchers

abstract class GherkinSpec
  extends TestKit(ActorSystem("SteamTestActorSystem"))
  with Matchers
  with OptionValues
  with Inside
  with Inspectors
  with ImplicitSender
  with GivenWhenThen
  with AnyFeatureSpecLike {

}

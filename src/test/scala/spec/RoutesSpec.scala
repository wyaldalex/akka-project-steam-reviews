package dev.galre.josue.steamreviews
package spec


import akka.http.scaladsl.testkit.{ RouteTestTimeout, ScalatestRouteTest }
import akka.testkit.TestDuration
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

import scala.concurrent.duration._

abstract class RoutesSpec
  extends AnyFeatureSpecLike
  with GivenWhenThen
  with ScalatestRouteTest
{

}

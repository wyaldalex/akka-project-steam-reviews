package dev.galre.josue.steamreviews
package spec


import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

abstract class RoutesSpec
  extends AnyFeatureSpecLike
  with GivenWhenThen
  with ScalatestRouteTest
{

}

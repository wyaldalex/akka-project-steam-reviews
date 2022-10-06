package dev.galre.josue

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, StandardRoute }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

package object akkaProject {

  case class Response(statusCode: Int, message: Option[String] = None)

  def completeWithFailure[T <: StatusCode](statusCode: T, message: Option[String]): StandardRoute =
    complete(statusCode, Response(statusCode.intValue(), message))

  def paginationParameters: Directive[(Int, Int)] =
    parameters("page".as[Int].withDefault(0), "perPage".as[Int].withDefault(50))

}

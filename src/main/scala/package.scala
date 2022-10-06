package dev.galre.josue

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, StandardRoute }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

package object akkaProject {

  case class ResponseWithMessage(statusCode: Int, message: Option[String] = None)

  case class ResponseWithPayload[P](statusCode: Int, message: P)

  def completeWithMessage[T <: StatusCode](statusCode: T, message: Option[String]): StandardRoute =
    complete(statusCode, ResponseWithMessage(statusCode.intValue(), message))

  def completeWithPayload[T <: StatusCode, P](statusCode: T = StatusCodes.OK, payload: P): StandardRoute =
    complete(statusCode, ResponseWithPayload(statusCode.intValue(), payload))

  def paginationParameters: Directive[(Int, Int)] =
    parameters("page".as[Int].withDefault(0), "perPage".as[Int].withDefault(50))

}

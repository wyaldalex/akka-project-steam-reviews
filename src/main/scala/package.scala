package dev.galre.josue

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.{ Directive, StandardRoute }
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

package object steamreviews {

  final case class ResponseWithMessage(statusCode: Int, message: Option[String] = None)

  private val DefaultPage = 0
  private val DefaultPerPageQuantity = 50

  def completeWithMessage[T <: StatusCode](statusCode: T, message: Option[String]): StandardRoute =
    complete(statusCode, ResponseWithMessage(statusCode.intValue(), message))

  def paginationParameters: Directive[(Int, Int)] =
    parameters(
      "page".as[Int].withDefault(DefaultPage),
      "perPage".as[Int].withDefault(DefaultPerPageQuantity)
    )

}

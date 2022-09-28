package dev.galre.josue

import scala.language.implicitConversions

package object akkaProject {
  case class NotFoundException(message: String) extends RuntimeException(message)

  case class UpdateFailedException(message: String) extends RuntimeException(message)

  case class GameAlreadyExistsException(message: String) extends RuntimeException(message)

  case class AlreadyExistsException(message: String) extends RuntimeException(message)

  case class Response(statusCode: Int, message: Option[String] = None)

  implicit def intToBigInt(value: Int): BigInt = BigInt(value)

  implicit def longToBigInt(value: Long): BigInt = BigInt(value)

  implicit def doubleToBigDecimal(value: Int): BigInt = BigInt(value)

  implicit def optionIntToOptionBigInt(value: Option[Int]): Option[BigInt] = value.flatMap(value => Option(BigInt(value)))

  implicit def optionLongToOptionBigInt(value: Option[Long]): Option[BigInt] = value.flatMap(value => Option(BigInt(value)))

  implicit def optionDoubleToOptionBigInt(value: Option[Double]): Option[BigDecimal] = value
    .flatMap(value => Option(BigDecimal(value)))

}

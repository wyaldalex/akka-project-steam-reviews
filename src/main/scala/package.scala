package dev.galre.josue

package object akkaProject {
  case class NotFoundException(message: String) extends RuntimeException(message)

  case class UpdateFailedException(message: String) extends RuntimeException(message)

  case class GameAlreadyExistsException(message: String) extends RuntimeException(message)

  case class AlreadyExistsException(message: String) extends RuntimeException(message)

  case class Response(statusCode: Int, message: Option[String] = None)

  def intToBigInt(value: Int): BigInt = BigInt(value)

  def longToBigInt(value: Long): BigInt = BigInt(value)

  def doubleToBigDecimal(value: Int): BigInt = BigInt(value)

  def optionIntToOptionBigInt(value: Option[Int]): Option[BigInt] = value.flatMap(value => Option(BigInt(value)))

  def optionLongToOptionBigInt(value: Option[Long]): Option[BigInt] = value.flatMap(value => Option(BigInt(value)))

  def optionDoubleToOptionBigDecimal(value: Option[Double]): Option[BigDecimal] = value
    .flatMap(value => Option(BigDecimal(value)))

}

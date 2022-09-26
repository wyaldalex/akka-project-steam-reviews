package dev.galre.josue

package object akkaProject {
  case class NotFoundException(message: String) extends RuntimeException(message)

  case class UpdateFailedException(message: String) extends RuntimeException(message)

  case class GameAlreadyExistsException(message: String) extends RuntimeException(message)

  case class AlreadyExistsException(message: String) extends RuntimeException(message)
}

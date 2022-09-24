package dev.galre.josue

package object akkaProject {
  case class NotFoundException(message: String) extends RuntimeException(message)

  case class AlreadyExistsException(message: String) extends RuntimeException(message)
}

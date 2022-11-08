package dev.galre.josue.steamreviews
package service.utils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object Binder {
  def apply(boundServer: Future[Http.ServerBinding])(implicit actorSystem: ActorSystem, executionContext: ExecutionContext)
  : Unit = {
    boundServer.onComplete {
      case Success(binding) =>
        val boundAddress = binding.localAddress
        val boundLocation = boundAddress.getAddress
        val boundPort = boundAddress.getPort

        actorSystem.log.info("Server started at: http://" + boundLocation + ":" + boundPort)

      case Failure(exception) =>
        actorSystem.log.error(s"Failed to bind server due to: $exception")
        actorSystem.terminate()
    }
  }
}
package dev.galre.josue.steamreviews
package service.utils

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object Config {

  def apply(): Config = {

    val config = ConfigFactory.load()

    val address = config.getString("config.http-address")
    val port = config.getInt("config.http-port")
    val timeout = config.getInt("config.timeout").seconds
    val actorSystemName = config.getString("config.actor-system-name")
    val nThreads = config.getInt("config.n-threads")

    Config(address, port, timeout, actorSystemName, nThreads)
  }
}

case class Config(
  address: String,
  port: Int,
  timeout: FiniteDuration,
  actorSystemName: String,
  nThreads: Int,
) {

  object Implicits {
    implicit def actorSystem: ActorSystem = ActorSystem(actorSystemName)

    implicit def executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(nThreads))

    implicit def timeOut: Timeout = Timeout(timeout)
  }
}

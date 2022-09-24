package dev.galre.josue.akkaProject
package http

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

case class GameRouter(gameManagerActor: ActorRef)(implicit timeout: Timeout) extends Directives {

  import actors.GameActor._

  def addGame(): Route =
    pathEndOrSingleSlash {
      post {
        entity(as[CreateGame]) { game =>
          onSuccess((gameManagerActor ? game).mapTo[GameCreatedResponse]) {
            case GameCreatedResponse(steamAppId) =>
              respondWithHeader(Location(s"/games/$steamAppId")) {
                complete(StatusCodes.Created)
              }
          }
        }
      }
    }

  val routes: Route =
    pathPrefix("games") {
      concat(
        addGame()
      )
    }
}

package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.user.UserActor.User

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.stream.alpakka.csv.scaladsl.{ CsvParsing, CsvToMap }
import akka.stream.scaladsl.{ FileIO, Flow, Sink }
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object CSVLoaderActor {
  // commands
  case class LoadCSV(file: String)

  def props(steamManagerActor: ActorRef)(implicit system: ActorSystem): Props =
    Props(new CSVLoaderActor(steamManagerActor))
}

class CSVLoaderActor(steamManagerActor: ActorRef)(implicit system: ActorSystem)
  extends Actor
  with ActorLogging {

  import CSVLoaderActor._
  import SteamManagerActor._

  val csvParserFlow: Flow[List[ByteString], CSVDataToLoad, NotUsed] =
    CsvToMap.toMapAsStrings(StandardCharsets.UTF_8).map(convertCSVData)

  override def receive: Receive = {
    case LoadCSV(file) =>
      FileIO
        .fromPath(Paths.get(file))
        .via(CsvParsing.lineScanner())
        .via(csvParserFlow)
        .runWith(
          Sink.actorRefWithBackpressure(
            ref = steamManagerActor,
            onInitMessage = InitCSVLoadToManagers,
            onCompleteMessage = FinishCSVLoad,
            onFailureMessage = CSVLoadFailure
          )
        )
  }

  def convertCSVData(row: Map[String, String]): CSVDataToLoad =
    CSVDataToLoad(
      review = Review(
        row("review_id").toLong,
        row("app_id").toLong,
        row("author.steamid").toLong,
        row.get("language"),
        row.get("review"),
        row.get("timestamp_created").flatMap(_.toLongOption),
        row.get("timestamp_updated").flatMap(_.toLongOption),
        row("recommended").toBooleanOption,
        row("votes_helpful").toLongOption,
        row("votes_funny").toLongOption,
        row("weighted_vote_score").toDoubleOption,
        row("comment_count").toLongOption,
        row("steam_purchase").toBooleanOption,
        row("received_for_free").toBooleanOption,
        row("written_during_early_access").toBooleanOption,
        row("author.playtime_forever").toDoubleOption,
        row("author.playtime_last_two_weeks").toDoubleOption,
        row("author.playtime_at_review").toDoubleOption,
        row("author.last_played").toDoubleOption
      ),
      user = User(
        userId = row("author.steamid").toInt,
        name = Some(s"user${row("author.steamid")}"),
        numGamesOwned = row("author.num_games_owned").toIntOption,
        numReviews = row("author.num_reviews").toIntOption
      ),
      game = GameState(
        steamAppId = row("app_id").toInt,
        steamAppName = row("app_name")
      )
    )

}

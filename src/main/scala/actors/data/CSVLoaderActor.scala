package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.user.UserActor.UserState

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

  val csvParserFlow: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    log.info("File read successfully!")
    CsvToMap.toMapAsStrings(StandardCharsets.UTF_8).map(convertCSVData)
  }

  override def receive: Receive = {
    case LoadCSV(file) =>
      log.info(s"reading file $file")

      FileIO
        .fromPath(Paths.get(file))
        .via(CsvParsing.lineScanner())
        .via(csvParserFlow)
        .runWith(
          Sink.actorRefWithBackpressure(
            ref = steamManagerActor,
            onInitMessage = InitCSVLoadToManagers,
            onCompleteMessage = FinishCSVLoadToManagers,
            onFailureMessage = CSVLoadFailure
          )
        )
  }

  def convertCSVData(row: Map[String, String]): CSVDataToLoad = {
    val reviewId         = longToBigInt(row("review_id").toLong)
    val steamAppId       = longToBigInt(row("app_id").toLong)
    val authorId         = longToBigInt(row("author.steamid").toLong)
    val timestampCreated = row.get("timestamp_created").flatMap(_.toLongOption)
    val timestampUpdated = row.get("timestamp_updated").flatMap(_.toLongOption)


    val review = ReviewState(
      reviewId,
      steamAppId,
      authorId,
      row.get("language"),
      row.get("review"),
      timestampCreated,
      timestampUpdated,
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
      optionLongToOptionBigInt(row("author.last_played").toLongOption)
    )
    val user   = UserState(
      userId = row("author.steamid").toLong,
      name = Some(s"user${row("author.steamid")}"),
      numGamesOwned = row("author.num_games_owned").toLongOption,
      numReviews = row("author.num_reviews").toLongOption
    )
    val game   = GameState(
      steamAppId = row("app_id").toLong,
      steamAppName = row("app_name")
    )

    CSVDataToLoad(
      review = review,
      user = user,
      game = game
    )
  }

}

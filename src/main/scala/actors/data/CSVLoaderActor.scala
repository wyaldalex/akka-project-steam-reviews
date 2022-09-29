package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.review.ReviewActor.ReviewState
import actors.user.UserActor.UserState

import akka.NotUsed
import akka.actor.SupervisorStrategy.Resume
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy }
import akka.stream.alpakka.csv.MalformedCsvException
import akka.stream.alpakka.csv.scaladsl.{ CsvParsing, CsvToMap }
import akka.stream.scaladsl.{ FileIO, Flow, Sink }
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.concurrent.duration._

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

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10) {
      case _: MalformedCsvException ⇒ Resume
    }

  override def receive: Receive = {
    case LoadCSV(file) =>
      log.info(s"reading file $file")

      FileIO
        .fromPath(Paths.get(file))
        .throttle(500, 3.seconds)
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
    val reviewId             = longToBigInt(row("review_id").toLong)
    val steamAppId           = longToBigInt(row("app_id").toLong)
    val authorId             = longToBigInt(row("author.steamid").toLong)
    val timestampCreated     = row.get("timestamp_created").flatMap(value => optionLongToOptionBigInt(value.toLongOption))
    val timestampUpdated     = row.get("timestamp_updated").flatMap(value => optionLongToOptionBigInt(value.toLongOption))
    val votesHelpful         = optionLongToOptionBigInt(row("votes_helpful").toLongOption)
    val votesFunny           = optionLongToOptionBigInt(row("votes_funny").toLongOption)
    val weightedVoteScore    = row("weighted_vote_score").toDoubleOption
    val commentCount         = optionLongToOptionBigInt(row("comment_count").toLongOption)
    val playtimeForever      = row("author.playtime_forever").toDoubleOption
    val playtimeLastTwoWeeks = row("author.playtime_last_two_weeks").toDoubleOption
    val playtimeAtReview     = row("author.playtime_at_review").toDoubleOption
    val lastPlayed           = row("author.last_played").toDoubleOption

    val review = ReviewState(
      reviewId,
      steamAppId,
      authorId,
      row.get("language"),
      row.get("review"),
      timestampCreated,
      timestampUpdated,
      row("recommended").toBooleanOption,
      votesHelpful,
      votesFunny,
      weightedVoteScore,
      commentCount,
      row("steam_purchase").toBooleanOption,
      row("received_for_free").toBooleanOption,
      row("written_during_early_access").toBooleanOption,
      playtimeForever,
      playtimeLastTwoWeeks,
      playtimeAtReview,
      lastPlayed
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

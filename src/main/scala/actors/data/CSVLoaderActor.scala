package dev.galre.josue.akkaProject
package actors.data

import actors.game.GameActor.GameState
import actors.review.ReviewActor.ReviewState
import actors.user.UserActor.UserState

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.stream.alpakka.csv.scaladsl.{ CsvParsing, CsvToMap }
import akka.stream.scaladsl.{ FileIO, Flow, Sink }
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.concurrent.duration._

object CSVLoaderActor {
  // commands
  case class LoadCSV(file: String, startPosition: Long = 0, numberOfElements: Int)

  def props(steamManagerActor: ActorRef)(implicit system: ActorSystem): Props =
    Props(new CSVLoaderActor(steamManagerActor))
}

class CSVLoaderActor(steamManagerActor: ActorRef)(implicit system: ActorSystem)
  extends Actor
  with ActorLogging {

  import CSVLoaderActor._
  import SteamManagerActor._

  def convertCSVData(row: Map[String, String]): CSVDataToLoad = {
    val reviewId                 = row("review_id").toLong
    val steamAppId               = row("app_id").toLong
    val authorId                 = row("author.steamid").toLong
    val region                   = row.get("language")
    val reviewValue              = row.get("review")
    val timestampCreated         = row.get("timestamp_created").flatMap(_.toLongOption)
    val timestampUpdated         = row.get("timestamp_updated").flatMap(_.toLongOption)
    val recommended              = row("recommended").toBooleanOption
    val votesHelpful             = row("votes_helpful").toLongOption
    val votesFunny               = row("votes_funny").toLongOption
    val weightedVoteScore        = row("weighted_vote_score").toDoubleOption
    val commentCount             = row("comment_count").toLongOption
    val steamPurchase            = row("steam_purchase").toBooleanOption
    val receivedForFree          = row("received_for_free").toBooleanOption
    val writtenDuringEarlyAccess = row("written_during_early_access").toBooleanOption
    val playtimeForever          = row("author.playtime_forever").toDoubleOption
    val playtimeLastTwoWeeks     = row("author.playtime_last_two_weeks").toDoubleOption
    val playtimeAtReview         = row("author.playtime_at_review").toDoubleOption
    val lastPlayed               = row.get("author.last_played").flatMap(value => value.toDoubleOption)

    val review = ReviewState(
      reviewId,
      steamAppId,
      authorId,
      region,
      reviewValue,
      timestampCreated,
      timestampUpdated,
      recommended,
      votesHelpful,
      votesFunny,
      weightedVoteScore,
      commentCount,
      steamPurchase,
      receivedForFree,
      writtenDuringEarlyAccess,
      playtimeForever,
      playtimeLastTwoWeeks,
      playtimeAtReview,
      lastPlayed
    )

    val name          = Some(s"user$authorId")
    val numGamesOwned = row("author.num_games_owned").toIntOption
    val numReviews    = row("author.num_reviews").toIntOption

    val user = UserState(
      authorId,
      name,
      numGamesOwned,
      numReviews
    )

    val steamAppName = row("app_name")

    val game = GameState(
      steamAppId,
      steamAppName
    )

    CSVDataToLoad(
      review,
      user,
      game
    )

  }

  val csvParserFlowFromZero: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap.toMapAsStrings(StandardCharsets.UTF_8).map(convertCSVData)
  }

  val csvParserFlowFromPosition: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap
      .withHeadersAsStrings(
        StandardCharsets.UTF_8,
        "",
        "app_id",
        "app_name",
        "review_id",
        "language",
        "review",
        "timestamp_created",
        "timestamp_updated",
        "recommended",
        "votes_helpful",
        "votes_funny",
        "weighted_vote_score",
        "comment_count",
        "steam_purchase",
        "received_for_free",
        "written_during_early_access",
        "author.steamid",
        "author.num_games_owned",
        "author.num_reviews",
        "author.playtime_forever",
        "author.playtime_last_two_weeks",
        "author.playtime_at_review",
        "author.last_played"
      )
      .map(convertCSVData)
  }

  def csvTransformerToDataEntities(startPosition: Long): Flow[List[ByteString], CSVDataToLoad, NotUsed] =
    if (startPosition == 0)
      csvParserFlowFromZero
    else
      csvParserFlowFromPosition

  override def receive: Receive = {
    case LoadCSV(file, startPosition, numberOfElements) =>
      log.info(s"reading file $file")

      FileIO.fromPath(Paths.get(file), 8192, startPosition)
        .via(CsvParsing.lineScanner(maximumLineLength = Int.MaxValue))
        .via(csvTransformerToDataEntities(startPosition))
        .throttle(1000, 3.seconds)
        .take(numberOfElements)
        .runWith(
          Sink.actorRefWithBackpressure(
            ref = steamManagerActor,
            onInitMessage = InitCSVLoadToManagers,
            onCompleteMessage = FinishCSVLoadToManagers,
            onFailureMessage = CSVLoadFailure
          )
        )
  }

}

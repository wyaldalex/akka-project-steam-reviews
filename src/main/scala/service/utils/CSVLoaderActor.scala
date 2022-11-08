package dev.galre.josue.steamreviews
package service.utils

import repository.GameManagerActor.CreateGameFromCSV
import repository.ReviewManagerActor.CreateReviewFromCSV
import repository.UserManagerActor.CreateUserFromCSV
import repository.entity.GameActor.GameState
import repository.entity.ReviewActor.ReviewState
import repository.entity.UserActor.UserState

import CSVLoaderActor._
import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.stream.alpakka.csv.scaladsl.{ CsvParsing, CsvToMap }
import akka.stream.scaladsl.{ FileIO, Flow, Sink }
import akka.util.ByteString

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.concurrent.duration._

object CSVLoaderActor {

  object CSVRows {
    val Index = ""
    val AppId = "app_id"
    val AppName = "app_name"
    val ReviewId = "review_id"
    val Language = "language"
    val Review = "review"
    val TimestampCreated = "timestamp_created"
    val TimestampUpdated = "timestamp_updated"
    val Recommended = "recommended"
    val VotesHelpful = "votes_helpful"
    val VotesFunny = "votes_funny"
    val WeightedVoteScore = "weighted_vote_score"
    val CommentCount = "comment_count"
    val SteamPurchase = "steam_purchase"
    val ReceivedForFree = "received_for_free"
    val WrittenDuringEarlyAccess = "written_during_early_access"
    val AuthorSteamId = "author.steamid"
    val AuthorNumGamesOwned = "author.num_games_owned"
    val AuthorNumReviews = "author.num_reviews"
    val AuthorPlaytimeForever = "author.playtime_forever"
    val AuthorPlaytimeLastTwoWeeks = "author.playtime_last_two_weeks"
    val AuthorPlaytimeAtReview = "author.playtime_at_review"
    val AuthorLastPlayed = "author.last_played"
  }

  case object InitCSVLoadToManagers

  case object FinishCSVLoadToManagers

  case object Ack

  final case class CSVLoadFailure(exception: Throwable)

  final case class CSVRow(
    reviewId: Long,
    steamAppId: Long,
    authorId: Long,
    region: Option[String],
    reviewValue: Option[String],
    timestampCreated: Option[Long],
    timestampUpdated: Option[Long],
    recommended: Option[Boolean],
    votesHelpful: Option[Long],
    votesFunny: Option[Long],
    weightedVoteScore: Option[Double],
    commentCount: Option[Long],
    steamPurchase: Option[Boolean],
    receivedForFree: Option[Boolean],
    writtenDuringEarlyAccess: Option[Boolean],
    playtimeForever: Option[Double],
    playtimeLastTwoWeeks: Option[Double],
    playtimeAtReview: Option[Double],
    lastPlayed: Option[Double],
    name: Option[String],
    numGamesOwned: Option[Int],
    numReviews: Option[Int],
    steamAppName: String,
    csvEntry: Long,
  )

  final case class CSVDataToLoad(
    csvEntryId: Long,
    review: ReviewState,
    user: UserState,
    game: GameState
  )

  // commands
  final case class LoadCSV(file: String, startPosition: Long = 0, numberOfElements: Int)

  def props(
    gameWriter: ActorRef,
    reviewWriter: ActorRef,
    userWriter: ActorRef
  )
    (implicit system: ActorSystem): Props =
    Props(new CSVLoaderActor(gameWriter, reviewWriter, userWriter))
}

class CSVLoaderActor(
  gameWriter: ActorRef,
  reviewWriter: ActorRef,
  userWriter: ActorRef
)
  (implicit system: ActorSystem)
  extends Actor
  with ActorLogging {

  def extractAndConvertRow(row: Map[String, String]): CSVRow = {
    val reviewId = row(CSVRows.ReviewId).toLong
    val steamAppId = row(CSVRows.AppId).toLong
    val authorId = row(CSVRows.AuthorSteamId).toLong
    val region = row.get(CSVRows.Language)
    val reviewValue = row.get(CSVRows.Review)
    val timestampCreated = row.get(CSVRows.TimestampCreated).flatMap(_.toLongOption)
    val timestampUpdated = row.get(CSVRows.TimestampUpdated).flatMap(_.toLongOption)
    val recommended = row(CSVRows.Recommended).toBooleanOption
    val votesHelpful = row(CSVRows.VotesHelpful).toLongOption
    val votesFunny = row(CSVRows.VotesFunny).toLongOption
    val weightedVoteScore = row(CSVRows.WeightedVoteScore).toDoubleOption
    val commentCount = row(CSVRows.CommentCount).toLongOption
    val steamPurchase = row(CSVRows.SteamPurchase).toBooleanOption
    val receivedForFree = row(CSVRows.ReceivedForFree).toBooleanOption
    val writtenDuringEarlyAccess = row(CSVRows.WrittenDuringEarlyAccess).toBooleanOption
    val playtimeForever = row(CSVRows.AuthorPlaytimeForever).toDoubleOption
    val playtimeLastTwoWeeks = row(CSVRows.AuthorPlaytimeLastTwoWeeks).toDoubleOption
    val playtimeAtReview = row(CSVRows.AuthorPlaytimeAtReview).toDoubleOption
    val lastPlayed = row.get(CSVRows.AuthorLastPlayed).flatMap(value => value.toDoubleOption)

    val name = Some("user" + authorId)
    val numGamesOwned = row(CSVRows.AuthorNumGamesOwned).toIntOption
    val numReviews = row(CSVRows.AuthorNumReviews).toIntOption

    val steamAppName = row(CSVRows.AppName)

    val csvEntry = row(CSVRows.Index).toLong

    CSVRow(
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
      lastPlayed,
      name,
      numGamesOwned,
      numReviews,
      steamAppName,
      csvEntry
    )
  }

  def convertCSVData(row: CSVRow): CSVDataToLoad = {
    val review = ReviewState(
      row.reviewId,
      row.steamAppId,
      row.authorId,
      row.region,
      row.reviewValue,
      row.timestampCreated,
      row.timestampUpdated,
      row.recommended,
      row.votesHelpful,
      row.votesFunny,
      row.weightedVoteScore,
      row.commentCount,
      row.steamPurchase,
      row.receivedForFree,
      row.writtenDuringEarlyAccess,
      row.playtimeForever,
      row.playtimeLastTwoWeeks,
      row.playtimeAtReview,
      row.lastPlayed
    )

    val user = UserState(
      row.authorId,
      row.name,
      row.numGamesOwned,
      row.numReviews
    )

    val game = GameState(
      row.steamAppId,
      row.steamAppName
    )

    CSVDataToLoad(
      row.csvEntry,
      review,
      user,
      game
    )
  }

  val csvParserFlowFromZero: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap
      .toMapAsStrings(StandardCharsets.UTF_8)
      .map(extractAndConvertRow)
      .map(convertCSVData)
  }

  val csvParserFlowFromPosition: Flow[List[ByteString], CSVDataToLoad, NotUsed] = {
    CsvToMap
      .withHeadersAsStrings(
        StandardCharsets.UTF_8,
        CSVRows.Index,
        CSVRows.AppId,
        CSVRows.AppName,
        CSVRows.ReviewId,
        CSVRows.Language,
        CSVRows.Review,
        CSVRows.TimestampCreated,
        CSVRows.TimestampUpdated,
        CSVRows.Recommended,
        CSVRows.VotesHelpful,
        CSVRows.VotesFunny,
        CSVRows.WeightedVoteScore,
        CSVRows.CommentCount,
        CSVRows.SteamPurchase,
        CSVRows.ReceivedForFree,
        CSVRows.WrittenDuringEarlyAccess,
        CSVRows.AuthorSteamId,
        CSVRows.AuthorNumGamesOwned,
        CSVRows.AuthorNumReviews,
        CSVRows.AuthorPlaytimeForever,
        CSVRows.AuthorPlaytimeLastTwoWeeks,
        CSVRows.AuthorPlaytimeAtReview,
        CSVRows.AuthorLastPlayed,
      )
      .map(extractAndConvertRow)
      .map(convertCSVData)
  }

  def csvTransformerToDataEntities(startPosition: Long): Flow[List[ByteString], CSVDataToLoad, NotUsed] =
    if (startPosition == 0) {
      csvParserFlowFromZero
    } else {
      csvParserFlowFromPosition
    }

  private val chunkSize = 8192
  private val elements = 1000

  override def receive: Receive = {
    case LoadCSV(file, startPosition, numberOfElements) =>
      log.info(s"reading file $file")

      FileIO.fromPath(Paths.get(file), chunkSize, startPosition)
        .via(CsvParsing.lineScanner(maximumLineLength = Int.MaxValue))
        .via(csvTransformerToDataEntities(startPosition))
        .throttle(elements, 3.seconds)
        .take(numberOfElements)
        .runWith(
          Sink.actorRefWithBackpressure(
            ref = self,
            onInitMessage = InitCSVLoadToManagers,
            onCompleteMessage = FinishCSVLoadToManagers,
            onFailureMessage = CSVLoadFailure
          )
        )

      sender() !
        s"Initialized CSV load of file $file with $numberOfElements elements at position $startPosition."

    // All CSVLoad messages
    case InitCSVLoadToManagers =>
      log.info("Initialized CSV Data load.")
      sender() ! Ack

    case CSVDataToLoad(csvEntryId, review, user, game) =>
      log.info(s"Received CSV Data for review ${review.reviewId} ($csvEntryId)")

      gameWriter ! CreateGameFromCSV(game)
      userWriter ! CreateUserFromCSV(user)
      reviewWriter ! CreateReviewFromCSV(review)

      sender() ! Ack

    case FinishCSVLoadToManagers =>
      log.info("Finished successfully CSV Load")

    case CSVLoadFailure(exception) =>
      log.error(
        s"CSV Load failed due to ${exception.getMessage}.\nStack trace: ${
          exception
            .printStackTrace()
        }\nexception: ${exception.toString}"
      )
  }

}

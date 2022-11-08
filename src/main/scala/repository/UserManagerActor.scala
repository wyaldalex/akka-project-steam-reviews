package dev.galre.josue.steamreviews
package repository

import repository.entity.UserActor
import repository.entity.UserActor._
import service.utils.{ Serializable, SnapshotSerializable }

import UserManagerActor._
import akka.actor.{ ActorLogging, Props }
import akka.persistence._
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.collection.mutable

object UserManagerActor {

  // users
  final case class UserManager(
    var userCount: Long = 0,
    users: mutable.HashMap[Long, UserController]
  ) extends Serializable

  val UserManagerSnapshotInterval = 1000

  // commands
  final case class CreateUserFromCSV(game: UserState)

  // events
  final case class UserActorCreated(id: Long) extends Serializable

  final case class UserActorCreatedFromCSV(id: Long) extends Serializable

  final case class UserActorDeleted(id: Long) extends Serializable

  // snapshot
  final case class UserManagerSnapshotSave(
    @JsonDeserialize(contentAs = classOf[Long]) userCount: Long,
    @JsonDeserialize(contentAs = classOf[Long]) userList: List[Long]
  ) extends SnapshotSerializable

  def props: Props = Props(new UserManagerActor())
}

class UserManagerActor
  extends PersistentActor
  with ActorLogging {


  var userManagerState: UserManager = UserManager(users = mutable.HashMap())

  override def persistenceId: String = "steam-user-manager"

  def isUserAvailable(id: Long): Boolean =
    userManagerState.users.contains(id) && !userManagerState.users(id).isDisabled

  def createActorName(steamUserId: Long): String = "steam-user-" + steamUserId

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"An user with the id $id couldn't be found")

  def tryToSaveSnapshot(): Unit = {
    if (lastSequenceNr % UserManagerSnapshotInterval == 0 && lastSequenceNr != 0) {
      val usersList = userManagerState.users.keys.toList
      val snapshotToSave = UserManagerSnapshotSave(userManagerState.userCount, usersList)
      log.info(s"Creating snapshot with ${usersList.size} entries on UserManagerActor")

      saveSnapshot(snapshotToSave)
    }
  }

  def checkAndForwardMessage(id: Long, message: Any): Unit = {
    if (isUserAvailable(id)) {
      userManagerState.users(id).actor.forward(message)
    } else {
      sender() ! notFoundExceptionCreator(id)
    }
  }

  override def receiveCommand: Receive = {
    case createCommand @ CreateUser(_, _, _) =>
      val steamUserId = userManagerState.userCount
      val userActorName = createActorName(steamUserId)
      val userActor = context.actorOf(
        UserActor.props(steamUserId),
        userActorName
      )
      val controlledUser = UserController(userActor)

      persist(UserActorCreated(steamUserId)) {
        _ =>
          userManagerState = userManagerState.copy(
            userCount = userManagerState.userCount + 1,
            users = userManagerState.users.addOne(steamUserId -> controlledUser)
          )

          tryToSaveSnapshot()

          userActor.forward(createCommand)
      }

    case getCommand @ GetUserInfo(id) =>
      checkAndForwardMessage(id, getCommand)

    case updateCommand @ UpdateUser(id, _, _, _) =>
      checkAndForwardMessage(id, updateCommand)

    case addOneReviewCommand @ AddOneReview(id) =>
      checkAndForwardMessage(id, addOneReviewCommand)

    case removeOneReviewCommand @ RemoveOneReview(id) =>
      checkAndForwardMessage(id, removeOneReviewCommand)

    case DeleteUser(id) =>
      if (isUserAvailable(id)) {
        persist(UserActorDeleted(id)) {
          _ =>
            userManagerState.users(id).isDisabled = true
            context.stop(userManagerState.users(id).actor)

            tryToSaveSnapshot()

            sender() ! Right(value = true)
        }
      } else {
        sender() ! notFoundExceptionCreator(id)
      }

    case CreateUserFromCSV(UserState(userId, name, numGamesOwned, numReviews)) =>
      if (!userManagerState.users.contains(userId)) {
        val userActor = context.actorOf(
          UserActor.props(userId),
          createActorName(userId)
        )
        val controlledUser = UserController(userActor)

        persist(UserActorCreatedFromCSV(userId)) {
          _ =>
            userManagerState = userManagerState.copy(
              users = userManagerState.users.addOne(userId -> controlledUser)
            )

            tryToSaveSnapshot()

            userActor ! CreateUser(name.getOrElse(""), numGamesOwned, numReviews)
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.info(getSavedSnapshotMessage("UserManagerActor", metadata))

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(getFailedSnapshotMessage("UserManagerActor", metadata, reason))
      reason.printStackTrace()

    case any: Any =>

  }

  def createUserFromRecover(steamUserId: Long): UserController = {
    val userActorName = createActorName(steamUserId)
    val userActor = context.child(userActorName)
      .getOrElse(
        context.actorOf(
          UserActor.props(steamUserId),
          userActorName
        )
      )

    UserController(userActor)
  }

  override def receiveRecover: Receive = {
    case UserActorCreated(steamUserId) =>
      val controlledUser = createUserFromRecover(steamUserId)

      userManagerState = userManagerState.copy(
        userCount = steamUserId + 1,
        userManagerState.users.addOne(steamUserId -> controlledUser)
      )

    case UserActorCreatedFromCSV(steamUserId) =>
      val controlledUser = createUserFromRecover(steamUserId)

      userManagerState = userManagerState.copy(
        users = userManagerState.users.addOne(steamUserId -> controlledUser)
      )

    case UserActorDeleted(id) =>
      userManagerState.users(id).isDisabled = true

    case SnapshotOffer(metadata, UserManagerSnapshotSave(userCount, userList)) =>
      log.info(s"Recovered user snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
      userManagerState = userManagerState.copy(userCount = userCount)

      userList.foreach {
        steamUserId =>
          val controlledUser = createUserFromRecover(steamUserId)

          userManagerState = userManagerState.copy(
            users = userManagerState.users.addOne(steamUserId -> controlledUser)
          )
      }

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")

  }
}

package dev.galre.josue.akkaProject
package actors.user

import actors.UserController
import actors.user.UserActor.UserState
import util.CborSerializable

import akka.actor.{ ActorLogging, Props }
import akka.persistence._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext


object UserManagerActor {

  // users
  case class UserManager(
    var userCount: Long = 0,
    users:         mutable.HashMap[Long, UserController]
  ) extends CborSerializable

  val userManagerSnapshotInterval = 1000

  // commands
  case class CreateUserFromCSV(game: UserState)

  // events
  case class UserActorCreated(id: Long) extends CborSerializable

  case class UserActorCreatedFromCSV(id: Long) extends CborSerializable

  case class UserActorDeleted(id: Long) extends CborSerializable

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new UserManagerActor())
}

class UserManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import UserActor._
  import UserManagerActor._

  var userManagerState: UserManager = UserManager(users = mutable.HashMap())

  override def persistenceId: String = "steam-user-manager"

  def isUserAvailable(id: Long): Boolean =
    userManagerState.users.contains(id) && !userManagerState.users(id).isDisabled

  def createActorName(steamUserId: Long): String = s"steam-user-$steamUserId"

  def notFoundExceptionCreator[T](id: Long): Either[String, T] =
    Left(s"An user with the id $id couldn't be found")

  def tryToSaveSnapshot(): Unit =
    if (lastSequenceNr % userManagerSnapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(userManagerState)

  override def receiveCommand: Receive = {
    case createCommand @ CreateUser(_, _, _) =>
      val steamUserId    = userManagerState.userCount
      val userActorName  = createActorName(steamUserId)
      val userActor      = context.actorOf(
        UserActor.props(steamUserId),
        userActorName
      )
      val controlledUser = UserController(userActor)

      persist(UserActorCreated(steamUserId)) { _ =>
        userManagerState = userManagerState.copy(
          userCount = userManagerState.userCount + 1,
          users = userManagerState.users.addOne(steamUserId -> controlledUser)
        )

        userActor.forward(createCommand)
      }

    case getCommand @ GetUserInfo(id) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(getCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case updateCommand @ UpdateUser(id, _, _, _) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(updateCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case addOneReviewCommand @ AddOneReview(id) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(addOneReviewCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case removeOneReviewCommand @ RemoveOneReview(id) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(removeOneReviewCommand)
      else
        sender() ! notFoundExceptionCreator(id)

    case DeleteUser(id) =>
      if (isUserAvailable(id))
        persist(UserActorDeleted(id)) { _ =>
          userManagerState.users(id).isDisabled = true
          context.stop(userManagerState.users(id).actor)

          sender() ! Right(true)
        }
      else
        sender() ! notFoundExceptionCreator(id)

    case CreateUserFromCSV(UserState(userId, name, numGamesOwned, numReviews)) =>
      if (!userManagerState.users.contains(userId)) {
        val userActor      = context.actorOf(
          UserActor.props(userId),
          createActorName(userId)
        )
        val controlledUser = UserController(userActor)

        persist(UserActorCreatedFromCSV(userId)) { _ =>
          userManagerState = userManagerState.copy(
            users = userManagerState.users.addOne(userId -> controlledUser)
          )

          userActor ! CreateUser(name.getOrElse(""), numGamesOwned, numReviews)
        }
      }

    case SaveSnapshotSuccess(metadata) =>
      log.debug(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")

    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")

    case any: Any =>
      log.debug(s"Got unhandled message: $any")

  }

  def createUserFromRecover(steamUserId: Long): UserController = {
    val userActorName = createActorName(steamUserId)
    val userActor     = context.child(userActorName)
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

    case SnapshotOffer(metadata, state: UserManager) =>
      log.info(s"Recovered snapshot ${metadata.persistenceId} - ${metadata.timestamp}")
      userManagerState = state

    case RecoveryCompleted =>
      log.info("Recovery completed successfully.")

  }
}

package dev.galre.josue.akkaProject
package actors.user

import actors.UserController

import akka.actor.{ ActorLogging, Props }
import akka.persistence.PersistentActor
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }


object UserManagerActor {

  // users
  case class UserManager(
    var userCount: BigInt = 0,
    users:         mutable.AnyRefMap[BigInt, UserController]
  )

  // events
  case class UserActorCreated(id: BigInt)

  case class UserActorDeleted(id: BigInt)

  def props(implicit timeout: Timeout, executionContext: ExecutionContext): Props = Props(new UserManagerActor())
}

class UserManagerActor(implicit timeout: Timeout, executionContext: ExecutionContext)
  extends PersistentActor
  with ActorLogging {

  import UserActor._
  import UserManagerActor._

  var userManagerState: UserManager = UserManager(users = mutable.AnyRefMap())

  override def persistenceId: String = "steam-user-manager"

  def isUserAvailable(id: BigInt): Boolean =
    userManagerState.users.contains(id) && !userManagerState.users(id).isDisabled

  def createActorName(steamUserId: BigInt): String = s"steam-user-$steamUserId"

  def notFoundExceptionCreator[T](id: BigInt): Try[T] =
    Failure(NotFoundException(s"An user with the id $id couldn't be found"))

  override def receiveCommand: Receive = {
    case createCommand @ CreateUser(name, numUsersOwned, numReviews) =>
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
        sender() ! GetUserInfoResponse(notFoundExceptionCreator(id))

    case updateCommand @ UpdateUser(id, _, _, _) =>
      if (isUserAvailable(id))
        userManagerState.users(id).actor.forward(updateCommand)
      else
        sender() ! UserUpdatedResponse(notFoundExceptionCreator(id))

    case DeleteUser(id) =>
      if (isUserAvailable(id))
        persist(UserActorDeleted(id)) { _ =>
          userManagerState.users(id).isDisabled = true
          context.stop(userManagerState.users(id).actor)

          sender() ! UserDeletedResponse(Success(true))
        }
      else
        sender() ! UserDeletedResponse(notFoundExceptionCreator(id))

  }

  override def receiveRecover: Receive = {
    case UserActorCreated(steamUserId) =>
      val userActorName = createActorName(steamUserId)
      val userActor     = context.child(userActorName)
        .getOrElse(
          context.actorOf(
            UserActor.props(steamUserId),
            userActorName
          )
        )

      val controlledUser = UserController(userActor)

      userManagerState = userManagerState.copy(
        userCount = steamUserId + 1,
        userManagerState.users.addOne(steamUserId -> controlledUser)
      )

    case UserActorDeleted(id) =>
      userManagerState.users(id).isDisabled = true

  }
}

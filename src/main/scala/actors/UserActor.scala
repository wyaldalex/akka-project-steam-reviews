package dev.galre.josue.akkaProject
package actors

import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{ Failure, Success, Try }

object UserActor {
  // 
  case class User(userId: Long, name: String = "", numGamesOwned: Int = 0, numReviews: Int = 0, isDeleted: Boolean = false)

  // commands
  case class CreateUser(name: String, numGamesOwned: Int, numReviews: Int)

  case class UpdateUser(name: String, numGamesOwned: Int, numReviews: Int)

  case object DeleteUser

  case object GetUserInfo


  // events
  case class UserCreated(user: User)

  case class UserUpdated(user: User)

  case object UserDeleted


  // responses
  case class UserCreatedResponse(id: Long)

  case class UserUpdatedResponse(maybeAccount: Try[User])

  case class GetUserInfoResponse(maybeAccount: Option[User])

  case class UserDeletedResponse(accountWasDeletedSuccessfully: Try[Boolean])


  def props(userId: Long): Props = Props(new UserActor(userId))
}

class UserActor(userId: Long) extends PersistentActor {

  import UserActor._

  var state: User = User(userId)

  override def persistenceId: String = s"steam-userId-$userId"

  def isUserNotDeleted: Boolean = !state.isDeleted

  def updateUser(newData: UpdateUser): User =
    User(
      userId,
      if (newData.name.isEmpty) state.name else newData.name,
      if (newData.numReviews == 0) state.numReviews else newData.numReviews,
      if (newData.numGamesOwned == 0) state.numGamesOwned else newData.numGamesOwned
    )

  override def receiveCommand: Receive = {
    case CreateUser(name, numGamesOwned, numReviews) if isUserNotDeleted =>
      val id = state.userId

      persist(UserCreated(User(id, name, numGamesOwned, numReviews))) { event =>
        state = event.user
        sender() ! UserCreatedResponse(id)
      }

    case command @ UpdateUser(newName, _, _) if isUserNotDeleted =>
      if (newName == state.name)
        sender() ! UserUpdatedResponse(
          Failure(new IllegalArgumentException("The new name cannot be equal to the previous one."))
        )
      else
        persist(UserUpdated(updateUser(command))) { event =>
          state = event.user
          sender() ! UserUpdatedResponse(Success(state))
        }

    case DeleteUser if isUserNotDeleted =>
      persist(UserDeleted) { _ =>
        state = state.copy(isDeleted = true)
        sender() ! UserDeletedResponse(Success(true))
      }

    case GetUserInfo if isUserNotDeleted =>
      sender() ! GetUserInfoResponse(Some(state))

    case CreateUser(_, _, _) | UpdateUser(_, _, _) | DeleteUser | GetUserInfo =>
      sender() ! new IllegalAccessException("The selected account does not exists.")
  }

  override def receiveRecover: Receive = {
    case UserCreated(user) =>
      state = user

    case UserUpdated(userUpdated) =>
      state = userUpdated

    case UserDeleted =>
      state = state.copy(isDeleted = true)
  }
}

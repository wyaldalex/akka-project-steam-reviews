package dev.galre.josue.akkaProject
package actors.user

import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{ Failure, Success, Try }

object UserActor {
  // state
  case class User(userId: BigInt, name: Option[String] = None, numGamesOwned: Option[Int] = None, numReviews: Option[Int] = None)

  // commands
  case class CreateUser(name: String, numGamesOwned: Option[Int], numReviews: Option[Int])

  case class UpdateUser(
    userId:        BigInt,
    name:          Option[String] = None,
    numGamesOwned: Option[Int] = None,
    numReviews:    Option[Int] = None
  )

  case class DeleteUser(userId: BigInt)

  case class GetUserInfo(userId: BigInt)


  // events
  case class UserCreated(user: User)

  case class UserUpdated(user: User)


  // responses
  case class UserCreatedResponse(id: Try[BigInt])

  case class UserUpdatedResponse(maybeAccount: Try[User])

  case class GetUserInfoResponse(maybeAccount: Try[User])

  case class UserDeletedResponse(accountWasDeletedSuccessfully: Try[Boolean])


  def props(userId: BigInt): Props = Props(new UserActor(userId))
}

class UserActor(userId: BigInt) extends PersistentActor {

  import UserActor._

  var state: User = User(userId)

  override def persistenceId: String = s"steam-userId-$userId"

  def updateUser(newData: UpdateUser): User =
    User(
      userId,
      if (newData.name.isEmpty) state.name else newData.name,
      if (newData.numGamesOwned.isEmpty) state.numGamesOwned else newData.numGamesOwned,
      if (newData.numReviews.isEmpty) state.numReviews else newData.numReviews,
    )

  override def receiveCommand: Receive = {
    case CreateUser(name, numGamesOwned, numReviews) =>
      val id = state.userId

      persist(UserCreated(User(id, Some(name), numGamesOwned, numReviews))) { event =>
        state = event.user
        sender() ! UserCreatedResponse(Success(id))
      }

    case command @ UpdateUser(_, newName, _, _) =>
      if (newName == state.name)
        sender() ! UserUpdatedResponse(
          Failure(new IllegalArgumentException("The new name cannot be equal to the previous one."))
        )
      else
        persist(UserUpdated(updateUser(command))) { event =>
          state = event.user
          sender() ! UserUpdatedResponse(Success(state))
        }

    case GetUserInfo(_) =>
      sender() ! GetUserInfoResponse(Success(state))

  }

  override def receiveRecover: Receive = {
    case UserCreated(user) =>
      state = user

    case UserUpdated(userUpdated) =>
      state = userUpdated

  }
}

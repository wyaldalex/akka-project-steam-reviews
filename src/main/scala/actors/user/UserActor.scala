package dev.galre.josue.akkaProject
package actors.user

import util.CborSerializable

import akka.actor.Props
import akka.persistence.PersistentActor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.util.{ Failure, Success, Try }

object UserActor {
  // state
  case class UserState(
    @JsonDeserialize(contentAs = classOf[Long]) userId:       Long,
    name:                                                     Option[String] = None,
    @JsonDeserialize(contentAs = classOf[Int]) numGamesOwned: Option[Int] = None,
    @JsonDeserialize(contentAs = classOf[Int]) numReviews:    Option[Int] = None
  ) extends CborSerializable

  // commands
  case class CreateUser(name: String, numGamesOwned: Option[Int], numReviews: Option[Int])

  case class UpdateUser(
    userId:        Long,
    name:          Option[String] = None,
    numGamesOwned: Option[Int] = None,
    numReviews:    Option[Int] = None
  )

  case class DeleteUser(userId: Long)

  case class GetUserInfo(userId: Long)


  // events
  case class UserCreated(user: UserState) extends CborSerializable

  case class UserUpdated(user: UserState) extends CborSerializable


  // responses
  case class UserCreatedResponse(id: Try[Long])

  case class UserUpdatedResponse(maybeAccount: Try[UserState])

  case class GetUserInfoResponse(maybeAccount: Try[UserState])

  case class UserDeletedResponse(accountWasDeletedSuccessfully: Try[Boolean])


  def props(userId: Long): Props = Props(new UserActor(userId))
}

class UserActor(userId: Long) extends PersistentActor {

  import UserActor._

  var state: UserState = UserState(userId)

  override def persistenceId: String = s"steam-userId-$userId"

  def updateUser(newData: UpdateUser): UserState =
    UserState(
      userId,
      if (newData.name.isEmpty) state.name else newData.name,
      if (newData.numGamesOwned.isEmpty) state.numGamesOwned else newData.numGamesOwned,
      if (newData.numReviews.isEmpty) state.numReviews else newData.numReviews,
    )

  override def receiveCommand: Receive = {
    case CreateUser(name, numGamesOwned, numReviews) =>
      val id = state.userId

      persist(UserCreated(UserState(id, Some(name), numGamesOwned, numReviews))) { event =>
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

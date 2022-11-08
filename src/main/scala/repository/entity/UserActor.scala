package dev.galre.josue.steamreviews
package repository.entity

import service.utils.Serializable

import UserActor._
import akka.actor.{ ActorRef, Props }
import akka.persistence.PersistentActor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

object UserActor {
  // state
  final case class UserState(
    @JsonDeserialize(contentAs = classOf[Long]) userId: Long,
    name: Option[String] = None,
    @JsonDeserialize(contentAs = classOf[Int]) numGamesOwned: Option[Int] = None,
    @JsonDeserialize(contentAs = classOf[Int]) numReviews: Option[Int] = None
  ) extends Serializable

  // commands
  final case class CreateUser(
    name: String,
    numGamesOwned: Option[Int],
    numReviews: Option[Int]
  )

  final case class UpdateUser(
    userId: Long,
    name: Option[String] = None,
    numGamesOwned: Option[Int] = None,
    numReviews: Option[Int] = None
  )

  final case class DeleteUser(userId: Long)

  final case class GetUserInfo(userId: Long)

  final case class AddOneReview(userId: Long)

  final case class RemoveOneReview(userId: Long)


  // events
  final case class UserCreated(user: UserState) extends Serializable

  final case class UserUpdated(user: UserState) extends Serializable


  // responses
  type UserCreatedResponse = Either[String, UserState]

  type UserUpdatedResponse = Either[String, UserState]

  type GetUserInfoResponse = Either[String, UserState]

  type UserDeletedResponse = Either[String, Boolean]

  type AddedOneReviewResponse = Either[String, Boolean]

  type RemovedOneReviewResponse = Either[String, Boolean]


  def props(userId: Long): Props = Props(new UserActor(userId))
}

class UserActor(userId: Long) extends PersistentActor {


  var state: UserState = UserState(userId)

  override def persistenceId: String = s"steam-userId-$userId"

  def updateUser(newData: UpdateUser): UserState = {
    val newName =
      if (newData.name.isEmpty) {
        state.name
      } else {
        newData.name
      }

    val newNumGamesOwned =
      if (newData.numGamesOwned.isEmpty) {
        state.numGamesOwned
      } else {
        newData.numGamesOwned
      }

    val newNumReviews =
      if (newData.numReviews.isEmpty) {
        state.numReviews
      } else {
        newData.numReviews
      }

    state.copy(
      name = newName,
      numGamesOwned = newNumGamesOwned,
      numReviews = newNumReviews
    )
  }

  def persistReviewCountChange(replyTo: ActorRef, newNumReviews: Option[Int]): Unit =
    persist(UserUpdated(state.copy(numReviews = newNumReviews))) {
      event =>
        state = event.user
        replyTo ! Right(value = true)
    }

  override def receiveCommand: Receive = {
    case CreateUser(name, numGamesOwned, numReviews) =>
      val id = state.userId

      persist(UserCreated(UserState(id, Some(name), numGamesOwned, numReviews))) {
        event =>
          state = event.user
          sender() ! Right(state)
      }

    case command @ UpdateUser(_, newName, _, _) =>
      if (newName == state.name) {
        sender() ! Left("The new name cannot be equal to the previous one.")
      } else {
        persist(UserUpdated(updateUser(command))) {
          event =>
            state = event.user
            sender() ! Right(state)
        }
      }

    case AddOneReview(_) =>
      val newNumReviews = for {
        current <- state.numReviews
      } yield {
        current + 1
      }

      persistReviewCountChange(sender(), newNumReviews)

    case RemoveOneReview(_) =>
      val newNumReviews = for {
        current <- state.numReviews
      } yield {
        current - 1
      }

      persistReviewCountChange(sender(), newNumReviews)

    case GetUserInfo(_) =>
      sender() ! Right(state)

  }

  override def receiveRecover: Receive = {
    case UserCreated(user) =>
      state = user

    case UserUpdated(userUpdated) =>
      state = userUpdated

  }
}

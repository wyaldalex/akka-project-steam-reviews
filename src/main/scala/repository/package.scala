package dev.galre.josue.steamreviews

import akka.actor.ActorRef
import akka.persistence.SnapshotMetadata

package object repository {
  final case class GameController(
    actor: ActorRef,
    var name: String,
    var isDisabled: Boolean = false
  )

  final case class UserController(
    actor: ActorRef,
    var isDisabled: Boolean = false
  )

  final case class ReviewController(
    actor: ActorRef,
    userId: Long,
    steamAppId: Long,
    var isDisabled: Boolean = false
  )

  def getSavedSnapshotMessage(actor: String, metadata: SnapshotMetadata): String =
    "Saving " + actor + " snapshot succeeded: " + metadata.persistenceId + " - " + metadata.timestamp

  def getFailedSnapshotMessage(actor: String, metadata: SnapshotMetadata, reason: Throwable): String =
    "Failed while trying to save " +
      actor +
      " snapshot succeeded: " +
      metadata.persistenceId +
      " - " +
      metadata.timestamp +
      "because of " + reason.getMessage

}

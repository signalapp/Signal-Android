package org.thoughtcrime.securesms.database

import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

/**
 * Represents a list of, or update to a list of, who can access a story through what
 * distribution lists, and whether they can reply.
 */
data class SentStorySyncManifest(
  val entries: List<Entry>
) {

  /**
   * Represents an entry in the proto manifest.
   */
  data class Entry(
    val recipientId: RecipientId,
    val allowedToReply: Boolean = false,
    val distributionLists: List<DistributionId> = emptyList()
  )

  /**
   * Represents a flattened entry that is more convenient for detecting data changes.
   */
  data class Row(
    val recipientId: RecipientId,
    val messageId: Long,
    val allowsReplies: Boolean,
    val distributionId: DistributionId
  )

  fun getDistributionIdSet(): Set<DistributionId> {
    return entries.map { it.distributionLists }.flatten().toSet()
  }

  fun toRecipientsSet(): Set<SignalServiceStoryMessageRecipient> {
    val recipients = Recipient.resolvedList(entries.map { it.recipientId })
    return recipients.map { recipient ->
      val serviceId = recipient.requireServiceId()
      val entry = entries.first { it.recipientId == recipient.id }

      SignalServiceStoryMessageRecipient(
        SignalServiceAddress(serviceId),
        entry.distributionLists.map { it.toString() },
        entry.allowedToReply
      )
    }.toSet()
  }

  fun flattenToRows(distributionIdToMessageIdMap: Map<DistributionId, Long>): Set<Row> {
    return entries.flatMap { getRowsForEntry(it, distributionIdToMessageIdMap) }.toSet()
  }

  private fun getRowsForEntry(entry: Entry, distributionIdToMessageIdMap: Map<DistributionId, Long>): List<Row> {
    return entry.distributionLists.map {
      Row(
        recipientId = entry.recipientId,
        allowsReplies = entry.allowedToReply,
        messageId = distributionIdToMessageIdMap[it] ?: -1L,
        distributionId = it
      )
    }.filterNot { it.messageId == -1L }
  }

  companion object {
    @WorkerThread
    @JvmStatic
    fun fromRecipientsSet(recipientsSet: Set<SignalServiceStoryMessageRecipient>): SentStorySyncManifest {
      val entries = recipientsSet.map { recipient ->
        Entry(
          recipientId = RecipientId.from(recipient.signalServiceAddress),
          allowedToReply = recipient.isAllowedToReply,
          distributionLists = recipient.distributionListIds.map { DistributionId.from(it) }
        )
      }

      return SentStorySyncManifest(entries)
    }

    fun fromRecipientsSet(recipients: List<SignalServiceProtos.SyncMessage.Sent.StoryMessageRecipient>): SentStorySyncManifest {
      val entries = recipients.toSet().map { recipient ->
        Entry(
          recipientId = RecipientId.from(ServiceId.parseOrThrow(recipient.destinationUuid)),
          allowedToReply = recipient.isAllowedToReply,
          distributionLists = recipient.distributionListIdsList.map { DistributionId.from(it) }
        )
      }

      return SentStorySyncManifest(entries)
    }
  }
}

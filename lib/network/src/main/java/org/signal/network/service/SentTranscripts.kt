/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.IdentityKey
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.SyncMessage

/** Builds the [SyncMessage.Sent] transcripts our linked devices get for messages we send. */
object SentTranscripts {

  /** [destination] is the recipient of a 1:1 send, and null for groups and stories. */
  fun forMessage(
    content: Content,
    timestamp: Long,
    results: List<MessageService.RecipientSendResult>,
    destination: ServiceId?,
    isRecipientUpdate: Boolean,
    pniIdentityKey: (ServiceId.PNI) -> IdentityKey?,
    storyMessageRecipients: List<SyncMessage.Sent.StoryMessageRecipient> = emptyList(),
    now: Long = System.currentTimeMillis()
  ): SyncMessage.Sent {
    val dataMessage = content.dataMessage?.let { if (it.isViewOnce == true) it.newBuilder().attachments(emptyList()).build() else it }

    return SyncMessage.Sent(
      destinationServiceIdBinary = destination?.toByteString(),
      timestamp = timestamp,
      message = dataMessage,
      storyMessage = content.storyMessage,
      editMessage = content.editMessage,
      expirationStartTimestamp = if ((dataMessage?.expireTimer ?: 0) > 0) now else null,
      unidentifiedStatus = results.mapNotNull { result ->
        val success = result.result.getOrNull() ?: return@mapNotNull null
        SyncMessage.Sent.UnidentifiedDeliveryStatus(
          destinationServiceIdBinary = result.serviceId.toByteString(),
          unidentified = success.sentSealedSender,
          destinationPniIdentityKey = (result.serviceId as? ServiceId.PNI)?.let(pniIdentityKey)?.publicKey?.serialize()?.toByteString()
        )
      },
      storyMessageRecipients = storyMessageRecipients.distinct(),
      isRecipientUpdate = isRecipientUpdate
    )
  }

  /** [storyMessage] is null for a recipient update that only changes who can see the story. */
  fun forStory(
    storyMessage: StoryMessage?,
    timestamp: Long,
    localAci: ServiceId.ACI,
    manifest: List<SyncMessage.Sent.StoryMessageRecipient>,
    isRecipientUpdate: Boolean
  ): SyncMessage.Sent {
    val self = localAci.toByteString()

    return SyncMessage.Sent(
      destinationServiceIdBinary = self,
      timestamp = timestamp,
      storyMessage = storyMessage,
      unidentifiedStatus = listOf(SyncMessage.Sent.UnidentifiedDeliveryStatus(destinationServiceIdBinary = self, unidentified = false)),
      storyMessageRecipients = manifest.distinct(),
      isRecipientUpdate = isRecipientUpdate
    )
  }
}

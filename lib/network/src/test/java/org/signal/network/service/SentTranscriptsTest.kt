/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.mockk
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.IdentityKeyPair
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.UUID

class SentTranscriptsTest {

  private val alice = ServiceId.ACI.from(UUID.randomUUID())
  private val bob = ServiceId.ACI.from(UUID.randomUUID())
  private val pni = ServiceId.PNI.from(UUID.randomUUID())
  private val localAci = ServiceId.ACI.from(UUID.randomUUID())
  private val pniIdentity = IdentityKeyPair.generate().publicKey

  private val timestamp = 1_700_000_000L
  private val now = 1_800_000_000L

  @Test
  fun `lists only successful results with their sealed status`() {
    val results = listOf(
      success(alice, sealed = true),
      success(bob, sealed = false),
      MessageService.RecipientSendResult(pni, Either.Left(MessageService.SendError.NotRegistered()))
    )

    val sent = forMessage(Content(dataMessage = DataMessage(body = "hi")), results)

    assertThat(sent.unidentifiedStatus.map { it.destinationServiceIdBinary to it.unidentified }).containsExactly(
      alice.toByteString() to true,
      bob.toByteString() to false
    )
  }

  @Test
  fun `PNI recipients carry the identity key we sent with`() {
    val sent = forMessage(Content(dataMessage = DataMessage(body = "hi")), listOf(success(pni, sealed = false), success(alice, sealed = true)))

    assertThat(sent.unidentifiedStatus.map { it.destinationPniIdentityKey }).containsExactly(pniIdentity.serialize().toByteString(), null)
  }

  @Test
  fun `view-once attachments are left out`() {
    val dataMessage = DataMessage(body = "once", isViewOnce = true, attachments = listOf(AttachmentPointer(cdnKey = "abc")))

    val sent = forMessage(Content(dataMessage = dataMessage), listOf(success(alice, sealed = true)))

    assertThat(sent.message!!.attachments).isEmpty()
    assertThat(sent.message!!.body).isEqualTo("once")
  }

  @Test
  fun `expiring messages start their timer now`() {
    val sent = forMessage(Content(dataMessage = DataMessage(body = "hi", expireTimer = 60)), listOf(success(alice, sealed = true)))

    assertThat(sent.expirationStartTimestamp).isEqualTo(now)
  }

  @Test
  fun `destination is set for a 1 to 1 send and left out for groups`() {
    val content = Content(dataMessage = DataMessage(body = "hi"))

    val direct = SentTranscripts.forMessage(content, timestamp, listOf(success(alice, sealed = true)), destination = alice, isRecipientUpdate = false, pniIdentityKey = { null }, now = now)
    val group = forMessage(content, listOf(success(alice, sealed = true)))

    assertThat(direct.destinationServiceIdBinary).isEqualTo(alice.toByteString())
    assertThat(group.destinationServiceIdBinary).isNull()
  }

  @Test
  fun `edits and stories are carried`() {
    val edit = EditMessage(targetSentTimestamp = 1L, dataMessage = DataMessage(body = "edited"))
    val story = StoryMessage(allowsReplies = true)

    assertThat(forMessage(Content(editMessage = edit), listOf(success(alice, sealed = true))).editMessage).isEqualTo(edit)
    assertThat(forMessage(Content(storyMessage = story), listOf(success(alice, sealed = true))).storyMessage).isEqualTo(story)
  }

  @Test
  fun `story transcript is addressed to ourselves with a deduplicated manifest`() {
    val recipient = SyncMessage.Sent.StoryMessageRecipient(destinationServiceIdBinary = alice.toByteString(), distributionListIds = listOf("list"), isAllowedToReply = true)

    val sent = SentTranscripts.forStory(storyMessage = null, timestamp = timestamp, localAci = localAci, manifest = listOf(recipient, recipient), isRecipientUpdate = true)

    assertThat(sent.destinationServiceIdBinary).isEqualTo(localAci.toByteString())
    assertThat(sent.unidentifiedStatus.map { it.destinationServiceIdBinary to it.unidentified }).containsExactly(localAci.toByteString() to false)
    assertThat(sent.storyMessageRecipients).containsExactly(recipient)
    assertThat(sent.isRecipientUpdate).isEqualTo(true)
    assertThat(sent.storyMessage).isNull()
  }

  private fun forMessage(content: Content, results: List<MessageService.RecipientSendResult>): SyncMessage.Sent {
    return SentTranscripts.forMessage(content, timestamp, results, destination = null, isRecipientUpdate = false, pniIdentityKey = { if (it == pni) pniIdentity else null }, now = now)
  }

  private fun success(serviceId: ServiceId, sealed: Boolean): MessageService.RecipientSendResult {
    return MessageService.RecipientSendResult(serviceId, Either.Right(MessageService.SendSuccess(envelopeContent = mockk(), sentSealedSender = sealed, devices = listOf(1))))
  }
}

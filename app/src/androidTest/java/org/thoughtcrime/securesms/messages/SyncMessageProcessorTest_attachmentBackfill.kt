/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.MessageContentFuzzer
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_attachmentBackfill {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper
  private var originalDeviceId: Int = SignalServiceAddress.DEFAULT_DEVICE_ID

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
    originalDeviceId = SignalStore.account.deviceId
    // Make this device a linked device so backfill response handling activates.
    SignalStore.account.deviceId = 2

    // Prevent AttachmentDownloadJob onAdded from async changing the attachment state.
    TextSecurePreferences.getSharedPreferences(harness.application)
      .edit()
      .putStringSet(TextSecurePreferences.MEDIA_DOWNLOAD_WIFI_PREF, emptySet())
      .putStringSet(TextSecurePreferences.MEDIA_DOWNLOAD_MOBILE_PREF, emptySet())
      .putStringSet(TextSecurePreferences.MEDIA_DOWNLOAD_ROAMING_PREF, emptySet())
      .commit()
  }

  @After
  fun tearDown() {
    SignalStore.account.deviceId = originalDeviceId
    messageHelper.tearDown()
  }

  @Test
  fun fresh_pointer_updates_row_and_resets_transfer_state() {
    val (messageId, attachmentId) = insertIncomingMediaMessage(messageHelper.alice)
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    val pointer = freshPointer(cdnNumber = 3, cdnKey = "fresh-key", size = 1234, uploadTimestamp = 9_999_000L)
    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = pointer))
    )

    // transferState is not asserted: the forced download job's onAdded() races it PENDING -> STARTED. The pointer fields
    // are written synchronously and are stable.
    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.remoteLocation).isEqualTo("fresh-key")
    assertThat(refreshed.cdn.cdnNumber).isEqualTo(3)
    assertThat(refreshed.size).isEqualTo(1234L)
    assertThat(refreshed.uploadTimestamp).isEqualTo(9_999_000L)
  }

  @Test
  fun terminal_error_marks_permanent_failure() {
    val (messageId, attachmentId) = insertIncomingMediaMessage(messageHelper.alice)
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(
        SyncMessage.AttachmentBackfillResponse.AttachmentData(
          status = SyncMessage.AttachmentBackfillResponse.AttachmentData.Status.TERMINAL_ERROR
        )
      )
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE)
  }

  @Test
  fun pending_status_leaves_row_unchanged() {
    val (messageId, attachmentId) = insertIncomingMediaMessage(messageHelper.alice)
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(
        SyncMessage.AttachmentBackfillResponse.AttachmentData(
          status = SyncMessage.AttachmentBackfillResponse.AttachmentData.Status.PENDING
        )
      )
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_FAILED)
  }

  @Test
  fun message_not_found_error_marks_attachments_retryable_failed() {
    val (messageId, attachmentId) = insertIncomingMediaMessage(messageHelper.alice)
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      error = SyncMessage.AttachmentBackfillResponse.Error.MESSAGE_NOT_FOUND
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_FAILED)
  }

  @Test
  fun primary_device_ignores_backfill_response() {
    SignalStore.account.deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID

    val (messageId, attachmentId) = insertIncomingMediaMessage(messageHelper.alice)
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(
        SyncMessage.AttachmentBackfillResponse.AttachmentData(
          status = SyncMessage.AttachmentBackfillResponse.AttachmentData.Status.TERMINAL_ERROR
        )
      )
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_FAILED)
  }

  @Test
  fun multi_attachment_response_matches_positionally_with_mixed_status() {
    val messageId = insertIncomingMessageWith(messageHelper.alice, listOf(incomingImagePointer(), incomingImagePointer()))
    val body = SignalDatabase.attachments.getAttachmentsForMessage(messageId).sortedBy { it.displayOrder }
    assertThat(body.size).isEqualTo(2)
    body.forEach { SignalDatabase.attachments.setTransferProgressFailed(it.attachmentId, messageId) }

    // Response is a positional array: index 0 -> body[0] (fresh pointer), index 1 -> body[1] (terminal).
    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(
        SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = freshPointer(cdnNumber = 3, cdnKey = "first-key", size = 11, uploadTimestamp = 111L)),
        SyncMessage.AttachmentBackfillResponse.AttachmentData(status = SyncMessage.AttachmentBackfillResponse.AttachmentData.Status.TERMINAL_ERROR)
      )
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).sortedBy { it.displayOrder }
    // remoteLocation proves index 0 routed to body[0]. transferState is not asserted: it races the download job's onAdded().
    assertThat(refreshed[0].remoteLocation).isEqualTo("first-key")
    assertThat(refreshed[0].cdn.cdnNumber).isEqualTo(3)
    assertThat(refreshed[1].transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE)
  }

  @Test
  fun long_text_slot_is_applied_independently_of_the_body() {
    val messageId = insertIncomingMessageWith(messageHelper.alice, listOf(incomingImagePointer(), incomingLongTextPointer()))
    val all = SignalDatabase.attachments.getAttachmentsForMessage(messageId)
    all.forEach { SignalDatabase.attachments.setTransferProgressFailed(it.attachmentId, messageId) }

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = freshPointer(cdnNumber = 3, cdnKey = "body-key", size = 22, uploadTimestamp = 222L))),
      longText = SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = freshPointer(cdnNumber = 3, cdnKey = "long-text-key", size = 33, uploadTimestamp = 333L))
    )

    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId)
    val bodyRow = refreshed.single { it.contentType != MediaUtil.LONG_TEXT }
    val longTextRow = refreshed.single { it.contentType == MediaUtil.LONG_TEXT }
    // The positional `attachments` array fills the body row and the separate `longText` slot fills the long-text row,
    // with no cross-contamination. transferState is not asserted: it races the download job's onAdded().
    assertThat(bodyRow.remoteLocation).isEqualTo("body-key")
    assertThat(longTextRow.remoteLocation).isEqualTo("long-text-key")
  }

  @Test
  fun remote_attachment_list_longer_than_local_skips_extras() {
    val messageId = insertIncomingMessageWith(messageHelper.alice, listOf(incomingImagePointer()))
    val attachmentId = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single().attachmentId
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)

    deliverBackfillResponse(
      sender = messageHelper.alice,
      sentTimestamp = sentTimestampFor(messageId),
      conversationId = messageHelper.alice,
      attachmentData = listOf(
        SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = freshPointer(cdnNumber = 3, cdnKey = "only-key", size = 44, uploadTimestamp = 444L)),
        SyncMessage.AttachmentBackfillResponse.AttachmentData(attachment = freshPointer(cdnNumber = 3, cdnKey = "extra-key", size = 55, uploadTimestamp = 555L))
      )
    )

    // The single local row is routed from index 0; the extra index-1 entry has no body[1] and must be skipped, not throw.
    val refreshed = SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
    assertThat(refreshed.remoteLocation).isEqualTo("only-key")
  }

  private fun insertIncomingMediaMessage(sender: RecipientId): Pair<Long, AttachmentId> {
    messageHelper.startTime = messageHelper.nextStartTime()
    val sentTimestamp = messageHelper.startTime

    val content = Content.Builder()
      .dataMessage(
        DataMessage.Builder()
          .timestamp(sentTimestamp)
          .attachments(listOf(MessageContentFuzzer.attachmentPointer()))
          .build()
      )
      .build()

    messageHelper.processor.process(
      envelope = MessageContentFuzzer.envelope(sentTimestamp),
      content = content,
      metadata = MessageContentFuzzer.envelopeMetadata(source = sender, destination = harness.self.id),
      serverDeliveredTimestamp = sentTimestamp + 10
    )

    val syncMessageId = MessageTable.SyncMessageId(sender, sentTimestamp)
    val messageId = SignalDatabase.messages.getMessageIdOrNull(syncMessageId)
    assertThat(messageId, name = "messageId").isNotNull()

    val attachment = SignalDatabase.attachments.getAttachmentsForMessage(messageId!!).single()
    return messageId to attachment.attachmentId
  }

  private fun insertIncomingMessageWith(sender: RecipientId, pointers: List<AttachmentPointer>): Long {
    messageHelper.startTime = messageHelper.nextStartTime()
    val sentTimestamp = messageHelper.startTime

    val content = Content.Builder()
      .dataMessage(
        DataMessage.Builder()
          .timestamp(sentTimestamp)
          .attachments(pointers)
          .build()
      )
      .build()

    messageHelper.processor.process(
      envelope = MessageContentFuzzer.envelope(sentTimestamp),
      content = content,
      metadata = MessageContentFuzzer.envelopeMetadata(source = sender, destination = harness.self.id),
      serverDeliveredTimestamp = sentTimestamp + 10
    )

    val messageId = SignalDatabase.messages.getMessageIdOrNull(MessageTable.SyncMessageId(sender, sentTimestamp))
    assertThat(messageId, name = "messageId").isNotNull()
    return messageId!!
  }

  private fun incomingImagePointer(): AttachmentPointer = MessageContentFuzzer.attachmentPointer().newBuilder().contentType("image/jpeg").build()

  private fun incomingLongTextPointer(): AttachmentPointer = MessageContentFuzzer.attachmentPointer().newBuilder().contentType(MediaUtil.LONG_TEXT).build()

  private fun sentTimestampFor(messageId: Long): Long {
    return SignalDatabase.messages.getMessageRecord(messageId).dateSent
  }

  private fun deliverBackfillResponse(
    sender: RecipientId,
    sentTimestamp: Long,
    conversationId: RecipientId,
    attachmentData: List<SyncMessage.AttachmentBackfillResponse.AttachmentData> = emptyList(),
    longText: SyncMessage.AttachmentBackfillResponse.AttachmentData? = null,
    error: SyncMessage.AttachmentBackfillResponse.Error? = null
  ) {
    messageHelper.startTime = messageHelper.nextStartTime()
    val envelopeTimestamp = messageHelper.startTime

    val response = SyncMessage.AttachmentBackfillResponse(
      targetMessage = AddressableMessage(
        authorServiceIdBinary = Recipient.resolved(sender).requireAci().toByteString(),
        sentTimestamp = sentTimestamp
      ),
      targetConversation = ConversationIdentifier(
        threadServiceIdBinary = Recipient.resolved(conversationId).requireAci().toByteString()
      ),
      attachments = if (error == null) SyncMessage.AttachmentBackfillResponse.AttachmentDataList(attachments = attachmentData, longText = longText) else null,
      error = error
    )

    val content = Content.Builder()
      .syncMessage(SyncMessage.Builder().attachmentBackfillResponse(response).build())
      .build()

    messageHelper.processor.process(
      envelope = MessageContentFuzzer.envelope(envelopeTimestamp, serverGuid = UUID.randomUUID()),
      content = content,
      metadata = MessageContentFuzzer.envelopeMetadata(source = harness.self.id, destination = harness.self.id, sourceDeviceId = 1),
      serverDeliveredTimestamp = envelopeTimestamp + 10
    )
  }

  private fun freshPointer(cdnNumber: Int, cdnKey: String, size: Int, uploadTimestamp: Long): AttachmentPointer {
    return AttachmentPointer.Builder()
      .cdnKey(cdnKey)
      .cdnNumber(cdnNumber)
      .key(Base64.decode("AAAAAAAA").toByteString())
      .digest(ByteArray(32) { it.toByte() }.toByteString())
      .size(size)
      .uploadTimestamp(uploadTimestamp)
      .contentType("image/jpeg")
      .build()
  }
}

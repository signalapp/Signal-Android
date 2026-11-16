/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.Base64
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.internal.util.Util
import java.util.UUID
import kotlin.time.Duration.Companion.days

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageTableTest_incomingEditAttachments {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var senderId: RecipientId
  private var threadId: Long = 0

  @Before
  fun setUp() {
    every { RemoteConfig.regularDeleteThreshold } returns 1.days.inWholeSeconds
    every { RemoteConfig.adminDeleteThreshold } returns 1.days.inWholeSeconds

    senderId = recipients.createRecipient("Sender Name")
    threadId = SignalDatabase.threads.getOrCreateThreadIdFor(senderId, false, ThreadTable.DistributionTypes.DEFAULT)
  }

  @Test
  fun editWithoutAttachmentsKeepsOriginalImage() {
    insertOriginal(sentTimeMillis = 1000, attachments = listOf(pointer(MediaUtil.IMAGE_JPEG)))

    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001, attachments = emptyList())

    assertThat(contentTypes(editId)).containsExactlyInAnyOrder(MediaUtil.IMAGE_JPEG)
  }

  @Test
  fun editWithLongTextKeepsOriginalImage() {
    insertOriginal(sentTimeMillis = 1000, attachments = listOf(pointer(MediaUtil.IMAGE_JPEG)))

    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001, attachments = listOf(pointer(MediaUtil.LONG_TEXT)))

    assertThat(contentTypes(editId)).containsExactlyInAnyOrder(MediaUtil.IMAGE_JPEG, MediaUtil.LONG_TEXT)
  }

  @Test
  fun editWithLongTextReplacesOriginalLongText() {
    insertOriginal(sentTimeMillis = 1000, attachments = listOf(pointer(MediaUtil.IMAGE_JPEG), pointer(MediaUtil.LONG_TEXT)))

    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001, attachments = listOf(pointer(MediaUtil.LONG_TEXT)))

    assertThat(contentTypes(editId)).containsExactlyInAnyOrder(MediaUtil.IMAGE_JPEG, MediaUtil.LONG_TEXT)
  }

  @Test
  fun editWithoutLongTextDropsOriginalLongText() {
    insertOriginal(sentTimeMillis = 1000, attachments = listOf(pointer(MediaUtil.IMAGE_JPEG), pointer(MediaUtil.LONG_TEXT)))

    val editId = insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001, attachments = emptyList())

    assertThat(contentTypes(editId)).containsExactlyInAnyOrder(MediaUtil.IMAGE_JPEG)
  }

  @Test
  fun editDoesNotChangeOriginalRevisionAttachments() {
    val originalId = insertOriginal(sentTimeMillis = 1000, attachments = listOf(pointer(MediaUtil.IMAGE_JPEG)))

    insertEdit(originalSentTimestamp = 1000, editSentTimeMillis = 1001, attachments = listOf(pointer(MediaUtil.LONG_TEXT)))

    assertThat(contentTypes(originalId)).isEqualTo(listOf(MediaUtil.IMAGE_JPEG))
  }

  private fun insertOriginal(sentTimeMillis: Long, attachments: List<Attachment>): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = sentTimeMillis,
      serverTimeMillis = sentTimeMillis,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "original message",
      attachments = attachments
    )
    return SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
  }

  private fun insertEdit(originalSentTimestamp: Long, editSentTimeMillis: Long, attachments: List<Attachment>): Long {
    val targetMessage = (SignalDatabase.messages.getMessageFor(originalSentTimestamp, senderId) as MmsMessageRecord).let {
      it.withAttachments(SignalDatabase.attachments.getAttachmentsForMessage(it.id))
    }

    val editMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderId,
      sentTimeMillis = editSentTimeMillis,
      serverTimeMillis = editSentTimeMillis,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "edited at $editSentTimeMillis",
      attachments = attachments
    )
    return SignalDatabase.messages.insertEditMessageInbox(editMessage, targetMessage).get().messageId
  }

  private fun contentTypes(messageId: Long): List<String?> {
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).map { it.contentType }
  }

  private fun pointer(contentType: String): PointerAttachment {
    return PointerAttachment(
      contentType = contentType,
      transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
      size = 100,
      fileName = null,
      cdn = Cdn.CDN_3,
      location = "location-${UUID.randomUUID()}",
      key = Base64.encodeWithPadding(Util.getSecretBytes(64)),
      iv = null,
      digest = Util.getSecretBytes(64),
      incrementalDigest = null,
      incrementalMacChunkSize = 0,
      fastPreflightId = null,
      voiceNote = false,
      borderless = false,
      videoGif = false,
      width = 100,
      height = 100,
      uploadTimestamp = System.currentTimeMillis(),
      caption = null,
      stickerLocator = null,
      blurHash = null,
      uuid = UUID.randomUUID(),
      quote = false,
      quoteTargetContentType = null
    )
  }
}

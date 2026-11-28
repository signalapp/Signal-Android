/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.ByteArrayInputStream
import java.util.Optional
import java.util.UUID
import kotlin.random.Random

/**
 * JVM (Robolectric) coverage for which rows [AttachmentTable.finalizeAttachmentAfterDownload] hands the downloaded data to. The attachment UUID and remote
 * location come straight from the sender's pointer, so they may only be used to find copies of the attachment within the edit history of the same message.
 */
@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentTableTest_finalizeAttachmentAfterDownload {

  @get:Rule val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId

  private val uuid = UUID.randomUUID()
  private val data = Random.nextBytes(64)
  private val otherData = Random.nextBytes(8)

  @Before
  fun setUp() {
    alice = recipients.createRecipient("Alice Android")
    bob = recipients.createRecipient("Bob Builder")
  }

  @Test
  fun `downloaded attachment is unchanged when another message reuses its uuid and remote location`() {
    val aliceMessageId = insertMessage(from = alice, sentTimeMillis = 1000, attachments = listOf(createAttachmentPointer(uuid)))
    download(aliceMessageId, data)
    val before = getAttachment(aliceMessageId)

    val bobMessageId = insertMessage(from = bob, sentTimeMillis = 1001, attachments = listOf(createAttachmentPointer(uuid, size = otherData.size)))
    download(bobMessageId, otherData)

    val after = getAttachment(aliceMessageId)
    assertThat(readData(after)).isEqualTo(data)
    assertThat(after.size).isEqualTo(before.size)
    assertThat(after.remoteKey).isEqualTo(before.remoteKey)
    assertThat(after.dataHash).isEqualTo(before.dataHash)
    assertThat(readData(getAttachment(bobMessageId))).isEqualTo(otherData)
  }

  @Test
  fun `pending attachment stays pending when another message reuses its uuid and remote location`() {
    val aliceMessageId = insertMessage(from = alice, sentTimeMillis = 1000, attachments = listOf(createAttachmentPointer(uuid)))
    val before = getAttachment(aliceMessageId)

    val bobMessageId = insertMessage(from = bob, sentTimeMillis = 1001, attachments = listOf(createAttachmentPointer(uuid, size = otherData.size)))
    download(bobMessageId, otherData)

    val after = getAttachment(aliceMessageId)
    assertThat(after.hasData).isFalse()
    assertThat(after.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_PENDING)
    assertThat(after.size).isEqualTo(before.size)
    assertThat(after.remoteKey).isEqualTo(before.remoteKey)
  }

  @Test
  fun `downloading the original revision fills pending attachments on all revisions`() {
    val originalId = insertMessage(from = alice, sentTimeMillis = 1000, attachments = listOf(createAttachmentPointer(uuid)))
    val edit1Id = insertEdit(from = alice, targetSentTimestamp = 1000, sentTimeMillis = 1001)
    val edit2Id = insertEdit(from = alice, targetSentTimestamp = 1001, sentTimeMillis = 1002)

    download(originalId, data)

    listOf(originalId, edit1Id, edit2Id).forEach { messageId ->
      val attachment = getAttachment(messageId)
      assertThat(attachment.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_DONE)
      assertThat(readData(attachment)).isEqualTo(data)
    }
  }

  @Test
  fun `downloading the latest revision fills pending attachments on all revisions`() {
    val originalId = insertMessage(from = alice, sentTimeMillis = 1000, attachments = listOf(createAttachmentPointer(uuid)))
    val edit1Id = insertEdit(from = alice, targetSentTimestamp = 1000, sentTimeMillis = 1001)
    val edit2Id = insertEdit(from = alice, targetSentTimestamp = 1001, sentTimeMillis = 1002)

    download(edit2Id, data)

    listOf(originalId, edit1Id, edit2Id).forEach { messageId ->
      val attachment = getAttachment(messageId)
      assertThat(attachment.transferState).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_DONE)
      assertThat(readData(attachment)).isEqualTo(data)
    }
  }

  @Test
  fun `downloaded previous revision is unchanged when an edit reuses its uuid and remote location`() {
    val originalId = insertMessage(from = alice, sentTimeMillis = 1000, attachments = listOf(createAttachmentPointer(uuid)))
    download(originalId, data)

    val editId = insertEdit(from = alice, targetSentTimestamp = 1000, sentTimeMillis = 1001, attachments = listOf(createAttachmentPointer(uuid, size = otherData.size)))
    val editAttachments = SignalDatabase.attachments.getAttachmentsForMessage(editId)
    val reusedPointer = editAttachments.single { !it.hasData }
    val copiedAttachment = editAttachments.single { it.attachmentId != reusedPointer.attachmentId }
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(editId, reusedPointer.attachmentId, ByteArrayInputStream(otherData))

    assertThat(readData(getAttachment(originalId))).isEqualTo(data)
    assertThat(readData(copiedAttachment)).isEqualTo(data)
  }

  private fun insertMessage(from: RecipientId, sentTimeMillis: Long, attachments: List<Attachment>): Long {
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(from, false, ThreadTable.DistributionTypes.DEFAULT)
    return SignalDatabase.messages.insertMessageInbox(createMessage(from, sentTimeMillis, attachments), threadId).get().messageId
  }

  private fun insertEdit(from: RecipientId, targetSentTimestamp: Long, sentTimeMillis: Long, attachments: List<Attachment> = emptyList()): Long {
    val targetMessage = SignalDatabase.messages.getMessageFor(targetSentTimestamp, from) as MmsMessageRecord
    return SignalDatabase.messages.insertEditMessageInbox(createMessage(from, sentTimeMillis, attachments), targetMessage).get().messageId
  }

  private fun createMessage(from: RecipientId, sentTimeMillis: Long, attachments: List<Attachment>): IncomingMessage {
    return IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = "sent at $sentTimeMillis",
      sentTimeMillis = sentTimeMillis,
      serverTimeMillis = sentTimeMillis,
      receivedTimeMillis = sentTimeMillis,
      attachments = attachments
    )
  }

  private fun download(messageId: Long, data: ByteArray) {
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, getAttachment(messageId).attachmentId, ByteArrayInputStream(data))
  }

  private fun getAttachment(messageId: Long): DatabaseAttachment {
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).single()
  }

  private fun readData(attachment: DatabaseAttachment): ByteArray {
    return SignalDatabase.attachments.getAttachmentStream(attachment.attachmentId, 0).use { it.readBytes() }
  }

  private fun createAttachmentPointer(uuid: UUID, size: Int = data.size): Attachment {
    return PointerAttachment.forPointer(
      pointer = Optional.of(
        SignalServiceAttachmentPointer(
          cdnNumber = 3,
          remoteId = SignalServiceAttachmentRemoteId.V4("asdf"),
          contentType = MediaUtil.IMAGE_JPEG,
          key = Random.nextBytes(64),
          size = Optional.of(size),
          preview = Optional.empty(),
          width = 2,
          height = 2,
          digest = Optional.of(Random.nextBytes(32)),
          incrementalDigest = Optional.empty(),
          incrementalMacChunkSize = 0,
          fileName = Optional.of("file.jpg"),
          voiceNote = false,
          isBorderless = false,
          isGif = false,
          caption = Optional.empty(),
          blurHash = Optional.empty(),
          uploadTimestamp = 0,
          uuid = uuid
        )
      )
    ).get()
  }
}

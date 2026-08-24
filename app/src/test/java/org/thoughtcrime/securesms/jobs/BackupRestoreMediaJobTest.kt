/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Coverage for which attachments get a thumbnail restore enqueued. Asking the CDN for a thumbnail that was never generated is guaranteed to 404, so the
 * enqueue side has to agree with the upload side about what can have one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BackupRestoreMediaJobTest {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val enqueuedJobs = mutableListOf<Job>()

  @Before
  fun setUp() {
    every { recipients.signalStore.backup.optimizeStorage } returns true

    // ArchiveRestoreProgress reads these while initializing, and BackupValues is a strict mock, so they have to exist before the object is touched at all.
    every { recipients.signalStore.backup.restoreState } returns RestoreState.NONE
    every { recipients.signalStore.backup.totalRestorableAttachmentSize } returns 0L
    mockkObject(ArchiveRestoreProgress)
    every { ArchiveRestoreProgress.onProcessStart() } just Runs

    every { AppDependencies.jobManager.add(capture(enqueuedJobs)) } returns Unit
  }

  @After
  fun tearDown() {
    unmockkObject(ArchiveRestoreProgress)
  }

  @Test
  fun givenAnImage_whenIEnqueue_thenIExpectAThumbnailRestore() {
    val image = givenRestorableAttachment(contentType = "image/jpeg")

    enqueue()

    assertThat(thumbnailRestoreTargets()).containsExactlyInAnyOrder(image)
  }

  @Test
  fun givenAVideo_whenIEnqueue_thenIExpectAThumbnailRestore() {
    val video = givenRestorableAttachment(contentType = "video/mp4")

    enqueue()

    assertThat(thumbnailRestoreTargets()).containsExactlyInAnyOrder(video)
  }

  /** A document never had a thumbnail generated, so the upload side never produced one to fetch. */
  @Test
  fun givenADocument_whenIEnqueue_thenIExpectNoThumbnailRestore() {
    givenRestorableAttachment(contentType = "application/pdf")

    enqueue()

    assertThat(thumbnailRestoreTargets()).isEmpty()
  }

  /** Stickers are image types, so only the sticker id keeps them out of thumbnail work on the upload side. */
  @Test
  fun givenASticker_whenIEnqueue_thenIExpectNoThumbnailRestore() {
    val sticker = givenRestorableAttachment(contentType = "image/webp")
    markAsSticker(sticker)

    enqueue()

    assertThat(thumbnailRestoreTargets()).isEmpty()
  }

  @Test
  fun givenAnSvg_whenIEnqueue_thenIExpectNoThumbnailRestore() {
    givenRestorableAttachment(contentType = "image/svg+xml")

    enqueue()

    assertThat(thumbnailRestoreTargets()).isEmpty()
  }

  @Test
  fun givenAQuote_whenIEnqueue_thenIExpectNoThumbnailRestore() {
    val quote = givenRestorableAttachment(contentType = "image/jpeg")
    markAsQuote(quote)

    enqueue()

    assertThat(thumbnailRestoreTargets()).isEmpty()
  }

  /** Skipping the job must not skip the state change, or the row would be re-selected by every later batch. */
  @Test
  fun givenAnIneligibleAttachment_whenIEnqueue_thenIExpectItStillOffloaded() {
    val document = givenRestorableAttachment(contentType = "application/pdf")

    enqueue()

    assertThat(transferStateOf(document)).isEqualTo(AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
  }

  /**
   * The batch that drains no jobs is the regression this guards. With a page size of 1 and three ineligible attachments, a loop keyed off the jobs it created
   * would stop after the first batch and silently leave the rest of the restore undone.
   */
  @Test
  fun givenMoreIneligibleAttachmentsThanOnePage_whenIEnqueue_thenIExpectEveryRowDrained() {
    repeat(3) { givenRestorableAttachment(contentType = "application/pdf") }

    enqueue(batchSize = 1)

    assertThat(needsRestoreCount()).isEqualTo(0)
  }

  @Test
  fun givenAMixOfEligibleAndNot_whenIEnqueue_thenIExpectOnlyTheEligibleOnesRequested() {
    val image = givenRestorableAttachment(contentType = "image/jpeg")
    givenRestorableAttachment(contentType = "application/pdf")
    val video = givenRestorableAttachment(contentType = "video/mp4")
    givenRestorableAttachment(contentType = "audio/mpeg")

    enqueue(batchSize = 2)

    assertThat(thumbnailRestoreTargets()).containsExactlyInAnyOrder(image, video)
    assertThat(needsRestoreCount()).isEqualTo(0)
  }

  private fun enqueue(batchSize: Int = 500) {
    BackupRestoreMediaJob().enqueueRestoreJobs(restoreTime = System.currentTimeMillis(), batchSize = batchSize)
  }

  private fun thumbnailRestoreTargets(): List<AttachmentId> {
    return enqueuedJobs.filterIsInstance<RestoreAttachmentThumbnailJob>().map { it.attachmentId }
  }

  /**
   * Inserts an attachment that needs restoring on a message old enough that optimize storage routes it down the thumbnail-only branch rather than a full-size
   * restore.
   */
  private fun givenRestorableAttachment(contentType: String): AttachmentId {
    val from = recipients.createRecipient("Contact ${UUID.randomUUID()}")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createAttachment(contentType))
    )

    val messageId = SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
    val attachmentId = SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId

    SignalDatabase.messages.writableDatabase
      .update(MessageTable.TABLE_NAME)
      .values(MessageTable.DATE_RECEIVED to System.currentTimeMillis() - 60.days.inWholeMilliseconds)
      .where("${MessageTable.ID} = ?", messageId)
      .run()

    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(AttachmentTable.TRANSFER_STATE to AttachmentTable.TRANSFER_NEEDS_RESTORE)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    return attachmentId
  }

  private fun markAsSticker(attachmentId: AttachmentId) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(AttachmentTable.STICKER_ID to 0)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }

  private fun markAsQuote(attachmentId: AttachmentId) {
    SignalDatabase.attachments.writableDatabase
      .update(AttachmentTable.TABLE_NAME)
      .values(AttachmentTable.QUOTE to 1)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
  }

  private fun transferStateOf(attachmentId: AttachmentId): Int {
    return SignalDatabase.attachments.readableDatabase
      .select(AttachmentTable.TRANSFER_STATE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
      .readToSingleInt(-1)
  }

  private fun needsRestoreCount(): Int {
    return SignalDatabase.attachments.readableDatabase
      .select(AttachmentTable.ID)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.TRANSFER_STATE} = ?", AttachmentTable.TRANSFER_NEEDS_RESTORE)
      .run()
      .readToList { it.requireLong(AttachmentTable.ID) }
      .size
  }

  private fun createAttachment(contentType: String): Attachment {
    return ArchivedAttachment(
      contentType = contentType,
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(32),
      cdnKey = "password",
      archiveCdn = 3,
      plaintextHash = Random.nextBytes(32),
      incrementalMac = null,
      incrementalMacChunkSize = null,
      width = 0,
      height = 0,
      caption = null,
      blurHash = null,
      voiceNote = false,
      borderless = false,
      stickerLocator = null,
      gif = false,
      quote = false,
      quoteTargetContentType = null,
      uuid = UUID.randomUUID(),
      fileName = null,
      localBackupKey = null
    )
  }
}

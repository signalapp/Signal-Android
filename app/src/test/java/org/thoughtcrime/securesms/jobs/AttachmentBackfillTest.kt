/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AttachmentBackfillTest {

  companion object {
    private var nextId: Long = 1000L
  }

  @get:Rule
  val mockSignalStore = MockSignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var jobManager: JobManager
  private lateinit var messages: MessageTable
  private lateinit var record: MessageRecord

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    every { mockSignalStore.account.isLinkedDevice } returns true
    every { mockSignalStore.account.isPrimaryDevice } returns false

    jobManager = AppDependencies.jobManager

    messages = mockk(relaxed = true)
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.messages } returns messages

    record = mockk(relaxed = true)
    every { record.isOutgoing } returns false
    every { messages.getMessageRecordOrNull(any()) } returns record
  }

  @After
  fun tearDown() {
    // Restore the production scope so a test-scheduler scope can't leak into the next test.
    AttachmentBackfill.timeoutScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    unmockkObject(SignalDatabase.Companion)
  }

  /**
   * Builds a real [DatabaseAttachment]. [Attachment.transferState]/[Attachment.quote] and [attachmentId] are read
   * directly by the backfill logic and can't be stubbed on a mock (they're @JvmField / final), so we construct a
   * concrete instance.
   */
  private fun databaseAttachment(
    attachmentId: AttachmentId = AttachmentId(1L),
    transferProgress: Int = AttachmentTable.TRANSFER_PROGRESS_FAILED,
    quote: Boolean = false,
    contentType: String = "image/jpeg",
    displayOrder: Int = 0
  ): DatabaseAttachment {
    return DatabaseAttachment(
      attachmentId = attachmentId,
      mmsId = 42L,
      hasData = false,
      hasThumbnail = false,
      contentType = contentType,
      transferProgress = transferProgress,
      size = 1_000L,
      fileName = "photo.jpg",
      cdn = Cdn.CDN_3,
      location = null,
      key = null,
      digest = null,
      incrementalDigest = null,
      incrementalMacChunkSize = 0,
      fastPreflightId = null,
      voiceNote = false,
      borderless = false,
      videoGif = false,
      width = 0,
      height = 0,
      quote = quote,
      caption = null,
      stickerLocator = null,
      blurHash = null,
      audioHash = null,
      transformProperties = null,
      displayOrder = displayOrder,
      uploadTimestamp = 0,
      dataHash = null,
      archiveCdn = null,
      thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE,
      archiveTransferState = AttachmentTable.ArchiveTransferState.NONE,
      uuid = null,
      quoteTargetContentType = null,
      metadata = null
    )
  }

  @Test
  fun `enqueues when all gates pass`() {
    val messageId = nextMessageId()

    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))

    verify(exactly = 1) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `skips when primary device`() {
    every { mockSignalStore.account.isPrimaryDevice } returns true
    val messageId = nextMessageId()

    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `skips when message record is missing`() {
    every { messages.getMessageRecordOrNull(any()) } returns null
    val messageId = nextMessageId()

    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `dedups consecutive requests for the same message`() {
    val messageId = nextMessageId()

    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))
    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))

    verify(exactly = 1) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `clearPending allows requeue for the same message`() {
    val messageId = nextMessageId()

    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))
    AttachmentBackfill.clearPending(messageId)
    AttachmentBackfill.maybeRequest(messageId, attachmentFor(messageId))

    verify(exactly = 2) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `skips quote attachments as ineligible kind`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId, quote = true))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `skips attachments on story messages`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)
    val storyRecord = mockk<MmsMessageRecord>(relaxed = true)
    every { storyRecord.storyType } returns StoryType.STORY_WITH_REPLIES
    every { messages.getMessageRecordOrNull(any()) } returns storyRecord

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `skips link-preview attachments as ineligible kind`() {
    val messageId = nextMessageId()
    val previewId = AttachmentId(messageId)
    val preview = mockk<LinkPreview>()
    every { preview.attachmentId } returns previewId
    val mmsRecord = mockk<MmsMessageRecord>(relaxed = true)
    every { mmsRecord.storyType } returns StoryType.NONE
    every { mmsRecord.linkPreviews } returns listOf(preview)
    every { mmsRecord.sharedContacts } returns emptyList()
    every { messages.getMessageRecordOrNull(any()) } returns mmsRecord

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = previewId))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
    assertFalse(AttachmentBackfill.awaiting.value.contains(previewId))
  }

  @Test
  fun `skips contact-share avatar attachments as ineligible kind`() {
    val messageId = nextMessageId()
    val avatarId = AttachmentId(messageId)
    val avatar = mockk<Contact.Avatar>()
    every { avatar.attachmentId } returns avatarId
    val contact = mockk<Contact>()
    every { contact.avatar } returns avatar
    val mmsRecord = mockk<MmsMessageRecord>(relaxed = true)
    every { mmsRecord.storyType } returns StoryType.NONE
    every { mmsRecord.linkPreviews } returns emptyList()
    every { mmsRecord.sharedContacts } returns listOf(contact)
    every { messages.getMessageRecordOrNull(any()) } returns mmsRecord

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = avatarId))

    verify(exactly = 0) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
    assertFalse(AttachmentBackfill.awaiting.value.contains(avatarId))
  }

  @Test
  fun `allows long-text overflow attachments`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId, contentType = "text/x-signal-plain"))

    verify(exactly = 1) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `backfillContractForMessage builds the positional body and longText contract both ends share`() {
    val messageId = nextMessageId()

    val body1Id = AttachmentId(101)
    val body2Id = AttachmentId(102)
    val longTextId = AttachmentId(103)
    val quoteId = AttachmentId(104)
    val previewId = AttachmentId(105)
    val contactId = AttachmentId(106)

    val body1 = databaseAttachment(attachmentId = body1Id, displayOrder = 1)
    val body2 = databaseAttachment(attachmentId = body2Id, displayOrder = 2)
    val longText = databaseAttachment(attachmentId = longTextId, displayOrder = 3, contentType = "text/x-signal-plain")
    val quote = databaseAttachment(attachmentId = quoteId, displayOrder = 4, quote = true)
    val preview = databaseAttachment(attachmentId = previewId, displayOrder = 5)
    val contact = databaseAttachment(attachmentId = contactId, displayOrder = 6)

    val linkPreview = mockk<LinkPreview>()
    every { linkPreview.attachmentId } returns previewId
    val avatar = mockk<Contact.Avatar>()
    every { avatar.attachmentId } returns contactId
    val sharedContact = mockk<Contact>()
    every { sharedContact.avatar } returns avatar

    val mmsRecord = mockk<MmsMessageRecord>(relaxed = true)
    every { mmsRecord.storyType } returns StoryType.NONE
    every { mmsRecord.linkPreviews } returns listOf(linkPreview)
    every { mmsRecord.sharedContacts } returns listOf(sharedContact)
    every { messages.getMessageRecordOrNull(any()) } returns mmsRecord

    val attachmentsTable = mockk<AttachmentTable>()
    every { SignalDatabase.attachments } returns attachmentsTable
    // Deliberately out of display order to prove the contract sorts.
    every { attachmentsTable.getAttachmentsForMessage(messageId) } returns listOf(body2, longText, quote, body1, preview, contact)

    val contract = AttachmentBackfill.backfillContractForMessage(messageId)

    // contract.body IS the positional wire array both ends share: index 0 -> body1, 1 -> body2.
    assertEquals(listOf(body1Id, body2Id), contract.bodyAttachments.map { it.attachmentId })
    assertEquals(longTextId, contract.longTextAttachment?.attachmentId)
  }

  @Test
  fun `flags the attachment as awaiting on request`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))

    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `final response keeps the attachment awaiting until its re-download terminates`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onResponseProcessed(messageId, anyPending = false)

    // The flag clears on the re-download terminal, not on the response — that's what avoids the retry flash.
    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `pending response keeps the attachment awaiting`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onResponseProcessed(messageId, anyPending = true)

    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `onAttachmentTerminal clears that attachment`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onAttachmentTerminal(attachmentId, messageId)

    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))
  }

  @Test
  fun `terminal of one attachment leaves the others of the message awaiting`() {
    val messageId = nextMessageId()
    val first = AttachmentId(messageId)
    val second = AttachmentId(-messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = first))
    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = second))

    AttachmentBackfill.onAttachmentTerminal(first, messageId)

    assertFalse(AttachmentBackfill.awaiting.value.contains(first))
    assertTrue(AttachmentBackfill.awaiting.value.contains(second))
  }

  @Test
  fun `last attachment terminal clears bookkeeping so the message can be re-requested`() {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onAttachmentTerminal(attachmentId, messageId)
    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))

    verify(exactly = 2) { jobManager.add(any<MultiDeviceAttachmentBackfillRequestJob>()) }
  }

  @Test
  fun `initial response timeout fires a TIMEOUT failure and clears awaiting`() = runTest {
    AttachmentBackfill.timeoutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)
    every { record.threadId } returns 77L

    val received = mutableListOf<AttachmentBackfill.BackfillFailure>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AttachmentBackfill.failures.collect { received += it }
    }

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))

    testScheduler.advanceUntilIdle()

    assertEquals(1, received.size)
    assertEquals(messageId, received.first().messageId)
    assertEquals(77L, received.first().threadId)
    assertEquals(AttachmentBackfill.FailureReason.TIMEOUT, received.first().reason)
    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))

    collector.cancel()
  }

  @Test
  fun `pending response extends the timeout beyond the initial window`() = runTest {
    AttachmentBackfill.timeoutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)
    every { record.threadId } returns 5L

    val received = mutableListOf<AttachmentBackfill.BackfillFailure>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AttachmentBackfill.failures.collect { received += it }
    }

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onResponseProcessed(messageId, anyPending = true)

    // Past the 30s initial window but within the 2m pending window: the extended timer hasn't fired.
    testScheduler.advanceTimeBy(60_000)
    testScheduler.runCurrent()
    assertTrue(received.isEmpty())
    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))

    // Past the 2m pending window: now it fires.
    testScheduler.advanceUntilIdle()
    assertEquals(1, received.size)
    assertEquals(AttachmentBackfill.FailureReason.TIMEOUT, received.first().reason)
    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))

    collector.cancel()
  }

  @Test
  fun `final response cancels the timeout so no failure is emitted`() = runTest {
    AttachmentBackfill.timeoutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)

    val received = mutableListOf<AttachmentBackfill.BackfillFailure>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AttachmentBackfill.failures.collect { received += it }
    }

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onResponseProcessed(messageId, anyPending = false)

    testScheduler.advanceUntilIdle()

    assertTrue(received.isEmpty())
    // Awaiting persists until the re-download terminates; the final response alone doesn't clear it.
    assertTrue(AttachmentBackfill.awaiting.value.contains(attachmentId))

    collector.cancel()
  }

  @Test
  fun `pending response for an untracked message starts no timeout`() = runTest {
    AttachmentBackfill.timeoutScope = CoroutineScope(StandardTestDispatcher(testScheduler))
    val messageId = nextMessageId()

    val received = mutableListOf<AttachmentBackfill.BackfillFailure>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AttachmentBackfill.failures.collect { received += it }
    }

    // No maybeRequest first, so the message isn't tracked: a stray pending response must not arm a timer.
    AttachmentBackfill.onResponseProcessed(messageId, anyPending = true)
    testScheduler.advanceUntilIdle()

    assertTrue(received.isEmpty())

    collector.cancel()
  }

  @Test
  fun `onResponseMessageNotFound clears awaiting and emits NOT_FOUND failure`() = runTest {
    val messageId = nextMessageId()
    val attachmentId = AttachmentId(messageId)
    val received = mutableListOf<AttachmentBackfill.BackfillFailure>()
    val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      AttachmentBackfill.failures.collect { received += it }
    }

    AttachmentBackfill.maybeRequest(messageId, databaseAttachment(attachmentId = attachmentId))
    AttachmentBackfill.onResponseMessageNotFound(messageId, threadId = 42L)

    assertFalse(AttachmentBackfill.awaiting.value.contains(attachmentId))
    assertEquals(1, received.size)
    assertEquals(messageId, received.first().messageId)
    assertEquals(42L, received.first().threadId)
    assertEquals(AttachmentBackfill.FailureReason.NOT_FOUND, received.first().reason)

    collector.cancel()
  }

  private fun attachmentFor(messageId: Long): DatabaseAttachment = databaseAttachment(attachmentId = AttachmentId(messageId))

  private fun nextMessageId(): Long {
    val id = nextId++
    AttachmentBackfill.clearPending(id)
    return id
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.verify
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.MediaEntry
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Coverage for the gate that decides whether a local copy may be deleted. The local copy is usually the only copy, so every refusal here is the difference
 * between a recoverable mistake and permanent data loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class OptimizeMediaJobTest {

  @get:Rule val recipients = RecipientTestRule()

  companion object {
    private const val NO_COMPLETED_CRAWL = -1L
    private val RECONCILIATION_INTERVAL = 7.days
    private val MEDIA_ROOT_BACKUP_KEY = MediaRootBackupKey(Random.nextBytes(32))
    private val PLAINTEXT_HASH = Random.nextBytes(32)
    private val REMOTE_KEY = Random.nextBytes(32)

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Before
  fun setUp() {
    every { recipients.signalStore.backup.mediaRootBackupKey } returns MEDIA_ROOT_BACKUP_KEY
  }

  @Test
  fun givenNoCrawlHasEverCompleted_whenIOffload_thenIExpectNothingOffloaded() {
    val attachmentId = givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = NO_COMPLETED_CRAWL, age = 1.days)

    offload()

    assertNotOffloaded(attachmentId)
  }

  /**
   * Refusing is safe but permanent on its own, since only a completed crawl can produce the evidence that lifts the refusal. Without this the feature would
   * appear to do nothing until the periodic crawl happened to run.
   */
  @Test
  fun givenNoCrawlHasEverCompleted_whenIOffload_thenIExpectACrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = NO_COMPLETED_CRAWL, age = 1.days)

    offload()

    assertACrawlWasRequested()
  }

  @Test
  fun givenFreshEvidence_whenIOffload_thenIExpectNoCrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = 1.days)

    offload()

    assertNoCrawlWasRequested()
  }

  /** The caller runs after every backup, so without a rate limit an unverified device would force a full walk of the server listing daily. */
  @Test
  fun givenACrawlWasForcedRecently_whenIOffload_thenIExpectNoCrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = NO_COMPLETED_CRAWL, age = 1.days)
    givenLastForcedAttempt(age = 1.days)

    offload()

    assertNoCrawlWasRequested()
  }

  @Test
  fun givenTheLastForcedCrawlIsOlderThanTheInterval_whenIOffload_thenIExpectACrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = NO_COMPLETED_CRAWL, age = 1.days)
    givenLastForcedAttempt(age = RECONCILIATION_INTERVAL * 2)

    offload()

    assertACrawlWasRequested()
  }

  /** Media can disappear from the CDN after a crawl confirmed it, so a confirmation has a shelf life even though it was genuine when recorded. */
  @Test
  fun givenTheEvidenceIsTooOld_whenIOffload_thenIExpectNothingOffloaded() {
    val attachmentId = givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = RECONCILIATION_INTERVAL * 5)

    offload()

    assertNotOffloaded(attachmentId)
  }

  @Test
  fun givenTheEvidenceIsTooOld_whenIOffload_thenIExpectACrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = RECONCILIATION_INTERVAL * 5)

    offload()

    assertACrawlWasRequested()
  }

  /**
   * A remotely configured interval of zero parses successfully rather than falling back to the default, so without a floor it would make every confirmation
   * instantly stale and stop offloading on every device at once.
   */
  @Test
  fun givenTheConfiguredIntervalIsZero_whenIOffload_thenIExpectItOffloadedAnyway() {
    val attachmentId = givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = 1.days)

    OptimizeMediaJob().offloadVerifiedMedia(minimumAge = 30.days, reconciliationInterval = Duration.ZERO)

    assertThat(transferStateOf(attachmentId)).isEqualTo(AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
  }

  /** The same floor has to reach the rate limit, or a zero interval would force a full walk of the server listing after every backup. */
  @Test
  fun givenTheConfiguredIntervalIsZeroAndACrawlWasJustForced_whenIOffload_thenIExpectNoCrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = NO_COMPLETED_CRAWL, age = 1.days)
    givenLastForcedAttempt(age = 1.hours)

    OptimizeMediaJob().offloadVerifiedMedia(minimumAge = 30.days, reconciliationInterval = Duration.ZERO)

    assertNoCrawlWasRequested()
  }

  /** A clock that moved backwards makes the evidence age negative, which has to read as untrustworthy rather than as brand new. */
  @Test
  fun givenTheClockRolledBack_whenIOffload_thenIExpectNothingOffloaded() {
    val attachmentId = givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = -(30.days))

    offload()

    assertNotOffloaded(attachmentId)
  }

  /** Refusing on its own would leave a far-future timestamp gating offloading until the clock caught up to it, so the evidence has to be discarded outright. */
  @Test
  fun givenTheClockRolledBack_whenIOffload_thenIExpectTheVerificationStateCleared() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = -(30.days))

    offload()

    val backup = recipients.signalStore.backup
    verify { backup.clearArchiveVerificationState() }
  }

  @Test
  fun givenTheClockRolledBack_whenIOffload_thenIExpectACrawlRequested() {
    givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = -(30.days))

    offload()

    assertACrawlWasRequested()
  }

  @Test
  fun givenFreshEvidenceForTheMedia_whenIOffload_thenIExpectItOffloaded() {
    val attachmentId = givenAnEligibleAttachmentConfirmedOnTheCdn()
    givenCompletedCrawl(snapshotVersion = 1, age = 1.days)

    offload()

    assertThat(transferStateOf(attachmentId)).isEqualTo(AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
    assertThat(dataFileOf(attachmentId)).isNull()
  }

  /** A snapshot row on its own is local bookkeeping. Only the crawl marking it seen makes it evidence the server produced. */
  @Test
  fun givenTheMediaWasNeverSeenOnTheCdn_whenIOffload_thenIExpectNothingOffloaded() {
    val attachmentId = givenAnEligibleAttachment()
    commitSnapshotFor(PLAINTEXT_HASH, REMOTE_KEY, markSeen = false)
    givenCompletedCrawl(snapshotVersion = 1, age = 1.days)

    offload()

    assertNotOffloaded(attachmentId)
  }

  private fun offload() {
    OptimizeMediaJob().offloadVerifiedMedia(minimumAge = 30.days, reconciliationInterval = RECONCILIATION_INTERVAL)
  }

  private fun givenCompletedCrawl(snapshotVersion: Long, age: Duration) {
    every { recipients.signalStore.backup.lastCompletedReconciliationSnapshotVersion } returns snapshotVersion
    every { recipients.signalStore.backup.lastCompletedReconciliationTime } returns System.currentTimeMillis() - age.inWholeMilliseconds
    givenLastForcedAttempt(age = null)
  }

  /** A null [age] means no crawl has ever been forced, which is what a device that just enabled this looks like. */
  private fun givenLastForcedAttempt(age: Duration?) {
    val timestamp = if (age == null) 0 else System.currentTimeMillis() - age.inWholeMilliseconds
    every { recipients.signalStore.backup.lastForcedReconciliationAttemptTime } returns timestamp
  }

  private fun givenAnEligibleAttachmentConfirmedOnTheCdn(): AttachmentId {
    val attachmentId = givenAnEligibleAttachment()
    commitSnapshotFor(PLAINTEXT_HASH, REMOTE_KEY, markSeen = true)
    return attachmentId
  }

  /** The offload path derives the media id from the hash and key, so the snapshot row has to carry the same derived value or it can never match. */
  private fun commitSnapshotFor(plaintextHash: ByteArray, remoteKey: ByteArray, markSeen: Boolean) {
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(MEDIA_ROOT_BACKUP_KEY).encode()

    SignalDatabase.backupMediaSnapshots.writePendingMediaEntries(
      listOf(MediaEntry(mediaId = mediaId, cdn = 3, plaintextHash = plaintextHash, remoteKey = remoteKey, isThumbnail = false))
    )
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    if (markSeen) {
      SignalDatabase.backupMediaSnapshots.markSeenOnRemote(listOf(mediaId), 1)
    }
  }

  private fun givenAnEligibleAttachment(): AttachmentId {
    val from = recipients.createRecipient("Some Contact")
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(from))

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      body = null,
      sentTimeMillis = 100L,
      serverTimeMillis = 100L,
      receivedTimeMillis = 200L,
      attachments = listOf(createAttachment())
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
      .values(
        AttachmentTable.TRANSFER_STATE to AttachmentTable.TRANSFER_PROGRESS_DONE,
        AttachmentTable.ARCHIVE_TRANSFER_STATE to AttachmentTable.ArchiveTransferState.FINISHED.value,
        AttachmentTable.DATA_FILE to "/not/a/real/file/${attachmentId.id}",
        AttachmentTable.DATA_HASH_END to Base64.encodeWithPadding(PLAINTEXT_HASH),
        AttachmentTable.REMOTE_KEY to Base64.encodeWithPadding(REMOTE_KEY),
        AttachmentTable.OFFLOAD_RESTORED_AT to 0
      )
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()

    return attachmentId
  }

  /** Deliberately not an image or video, so eligibility does not additionally depend on a local thumbnail existing. */
  private fun createAttachment(): Attachment {
    return ArchivedAttachment(
      contentType = "application/pdf",
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

  /**
   * The job manager is hoisted out of the verify block deliberately. [AppDependencies] is statically mocked, so referencing it inside the block would record
   * the accessor itself as a call to verify.
   */
  private fun assertACrawlWasRequested() {
    val jobManager = AppDependencies.jobManager
    verify { jobManager.add(ofType<ArchiveAttachmentReconciliationJob>()) }
  }

  private fun assertNoCrawlWasRequested() {
    val jobManager = AppDependencies.jobManager
    verify(exactly = 0) { jobManager.add(ofType<ArchiveAttachmentReconciliationJob>()) }
  }

  private fun assertNotOffloaded(attachmentId: AttachmentId) {
    assertThat(transferStateOf(attachmentId)).isEqualTo(AttachmentTable.TRANSFER_PROGRESS_DONE)
    assertThat(dataFileOf(attachmentId)).isNotNull()
  }

  private fun transferStateOf(attachmentId: AttachmentId): Int {
    return SignalDatabase.attachments.readableDatabase
      .select(AttachmentTable.TRANSFER_STATE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
      .readToSingleInt(-1)
  }

  private fun dataFileOf(attachmentId: AttachmentId): String? {
    return SignalDatabase.attachments.readableDatabase
      .select(AttachmentTable.DATA_FILE)
      .from(AttachmentTable.TABLE_NAME)
      .where("${AttachmentTable.ID} = ?", attachmentId.id)
      .run()
      .readToSingleObject { it.requireString(AttachmentTable.DATA_FILE) }
  }
}

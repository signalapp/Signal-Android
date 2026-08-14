/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.backup.MediaName
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.network.api.ArchiveApiV2
import org.signal.network.service.ArchiveError
import org.signal.network.service.ArchiveService
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.MediaEntry
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ArchiveAttachmentReconciliationJobTest {

  @get:Rule
  val harness = SignalActivityRule()

  private val archiveService: ArchiveService = AppDependencies.archiveService

  private val deletedFromCdn = slot<Set<ArchivedMediaObject>>()

  private val watchedJobKeys = setOf(ArchiveAttachmentBackfillJob.KEY, ArchiveThumbnailBackfillJob.KEY, BackupMessagesJob.KEY)
  private val enqueuedJobKeys: MutableList<String> = CopyOnWriteArrayList()
  private val jobListener = JobTracker.JobListener { job, _ -> enqueuedJobKeys += job.factoryKey }

  @Before
  fun setUp() {
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    SignalStore.backup.hasBackupBeenUploaded = true
    SignalStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis()
    SignalStore.backup.localRestoreReconcilePending = false
    SignalStore.backup.lastUsedMessageCutoffTime = 0

    AppDependencies.jobManager.addListener(JobTracker.JobFilter { it.factoryKey in watchedJobKeys }, jobListener)

    mockkObject(BackupMessagesJob)
    every { BackupMessagesJob.enqueue() } just Runs

    mockkObject(ArchiveCommitAttachmentDeletesJob)
    coEvery { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), capture(deletedFromCdn), any(), any()) } returns null
  }

  @After
  fun tearDown() {
    AppDependencies.jobManager.removeListener(jobListener)
    enqueuedJobKeys.clear()
    unmockkAll()
  }

  /**
   * The core of the reconcile-first restore flow: a local restore resets everything to [AttachmentTable.ArchiveTransferState.NONE], so media that genuinely is
   * on the CDN must be promoted back to FINISHED during reconciliation -- otherwise the backfill would needlessly re-upload it. This only happens while
   * [localRestoreReconcilePending] is set, so it never runs in the common periodic reconciliation.
   */
  @Test
  fun givenLocalRestorePendingAndAttachmentOnCdn_whenIReconcile_thenIExpectItMarkedFinished() {
    SignalStore.backup.localRestoreReconcilePending = true

    val attachmentId = seedFinalizedAttachment("remote-key-1".toByteArray(), byteArrayOf(1, 2, 3, 4, 5))
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  /**
   * Guards the reconcile-first promotion above: outside the local-restore flow (the common periodic reconciliation), NONE media that happens to be on the CDN is
   * left alone, so we don't do the expensive mark-finished scan in the common case.
   */
  @Test
  fun givenNoLocalRestorePendingAndNoneAttachmentOnCdn_whenIReconcile_thenItStaysNone() {
    val attachmentId = seedFinalizedAttachment("remote-key-common".toByteArray(), byteArrayOf(2, 3, 4, 5, 6))
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  @Test
  fun givenFinishedAttachmentMissingFromCdn_whenIReconcile_thenIExpectItResetToNone() {
    val attachmentId = seedFinalizedAttachment("remote-key-2".toByteArray(), byteArrayOf(6, 7, 8, 9, 10))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  /**
   * The eventual safety net: an ordinary (non-forced) periodic reconciliation, run after the sync interval has elapsed, heals the bad state on its own -- media
   * in the snapshot but missing from the CDN is reset to [AttachmentTable.ArchiveTransferState.NONE] and a re-upload is enqueued -- with no help from the
   * migration or the reconcile-first flow.
   */
  @Test
  fun givenFinishedMediaMissingFromCdn_whenAnOrdinaryPeriodicReconciliationRuns_thenItHealsToNoneAndReUploadsWithoutANewBackup() {
    SignalStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis() - 60.days.inWholeMilliseconds

    val attachmentId = seedFinalizedAttachment("remote-key-periodic".toByteArray(), byteArrayOf(1, 2, 3, 4, 5))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = false).run()

    val healed = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(healed.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(healed.archiveCdn).isNull()
    assertThat(SignalDatabase.attachments.doAnyAttachmentsNeedArchiveUpload()).isTrue()
    assertThat(awaitEnqueuedJob(ArchiveAttachmentBackfillJob.KEY)).isTrue()
    assertThat(enqueuedJobKeys).doesNotContain(BackupMessagesJob.KEY)
    verify(exactly = 0) { BackupMessagesJob.enqueue() }
  }

  /**
   * Reconciliation must only repair genuinely-broken state. Media that is actually present on the CDN stays [AttachmentTable.ArchiveTransferState.FINISHED], so
   * we never needlessly reset (and therefore re-upload) media that was correctly archived.
   */
  @Test
  fun givenFinishedMediaStillOnCdn_whenIReconcile_thenItStaysFinished() {
    val attachmentId = seedFinalizedAttachment("remote-key-on-cdn".toByteArray(), byteArrayOf(6, 7, 8, 9, 10))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  /**
   * The other healing direction: media that is on the CDN but locally marked [AttachmentTable.ArchiveTransferState.NONE] and absent from the current snapshot is
   * treated as a delete-candidate. Before deleting, reconciliation confirms it's still referenced locally and recovers it to
   * [AttachmentTable.ArchiveTransferState.FINISHED] rather than deleting it from the CDN.
   */
  @Test
  fun givenNoneMediaOnCdnButNotInSnapshot_whenIReconcile_thenItIsRecoveredToFinished() {
    val attachmentId = seedFinalizedAttachment("remote-key-flow2".toByteArray(), byteArrayOf(11, 12, 13, 14, 15))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  /**
   * The over-size-limit cutoff leaves old messages out of the export, so nothing in the backup references their media any more. Reconciliation applies the same
   * rule as [ArchiveCommitAttachmentDeletesJob] so the two reclaim paths can't disagree about what still counts as referenced.
   */
  @Test
  fun givenCdnMediaWhoseMessagePredatesTheMessageCutoff_whenIReconcile_thenIExpectItDeletedFromTheCdn() {
    val attachmentId = seedFinalizedAttachment("remote-key-cutoff".toByteArray(), byteArrayOf(21, 22, 23, 24, 25), receivedAt = 10.days)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(deletedFromCdn.captured.map { it.mediaId }).contains(mediaIdFor(attachmentId))
  }

  /**
   * A cutoff being set at all must not weaken the protection for media the backup still references, which is what keeps a lost snapshot table from wiping the
   * archive.
   */
  @Test
  fun givenCdnMediaWhoseMessageIsWithinTheMessageCutoff_whenIReconcile_thenIExpectItProtected() {
    val attachmentId = seedFinalizedAttachment("remote-key-recent".toByteArray(), byteArrayOf(26, 27, 28, 29, 30), receivedAt = 40.days)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  @Test
  fun givenFirstEverReconciliation_whenIForceIt_thenItStillRunsAndRepairs() {
    SignalStore.backup.lastAttachmentReconciliationTime = -1

    val attachmentId = seedFinalizedAttachment("remote-key-3".toByteArray(), byteArrayOf(11, 12, 13, 14, 15))
    SignalDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  /**
   * A local restore imports the CDN numbers its backup file claimed and optimistically marks them finished, so the export that ran ahead of this crawl published
   * claims the crawl has since corrected. A full backup is what republishes the verified state, unlike the media-only repair the periodic path does.
   */
  @Test
  fun givenLocalRestoreReconcilePending_whenReconcileCompletes_thenIExpectFlagClearedAndABackup() {
    SignalStore.backup.localRestoreReconcilePending = true
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.localRestoreReconcilePending).isFalse()
    verify(exactly = 1) { BackupMessagesJob.enqueue() }
  }

  /**
   * Media that was offloaded (or restored from a backup) has no local data file, so resetting it to NONE cannot lead to a re-upload. It only makes the export
   * emit a tombstone, which drops the locator and marks the media for deletion off of the CDN. It must stay FINISHED.
   */
  @Test
  fun givenFinishedMediaMissingFromCdnWithNoLocalDataFile_whenIReconcile_thenItStaysFinishedAndKeepsItsCdn() {
    val attachmentId = seedArchivedAttachment()
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    val after = SignalDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(after.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
    assertThat(after.archiveCdn).isEqualTo(3)
  }

  @Test
  fun givenACrawlThatCompletes_whenIReconcile_thenIExpectTheCompletedSnapshotVersionRecorded() {
    SignalStore.backup.lastCompletedReconciliationSnapshotVersion = -1

    val attachmentId = seedFinalizedAttachment("remote-key-recorded".toByteArray(), byteArrayOf(1, 2, 3, 4, 5))
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.lastCompletedReconciliationSnapshotVersion).isEqualTo(SignalDatabase.backupMediaSnapshots.getCurrentSnapshotVersion())
  }

  /**
   * Offloading trusts the recorded version as proof the server confirmed our media, so a crawl that dies part way through must leave it untouched rather than
   * recording a version it never finished verifying.
   */
  @Test
  fun givenACrawlThatFailsPartWayThrough_whenIReconcile_thenIExpectNoCompletedSnapshotVersionRecorded() {
    SignalStore.backup.lastCompletedReconciliationSnapshotVersion = -1

    val attachmentId = seedFinalizedAttachment("remote-key-failed".toByteArray(), byteArrayOf(2, 3, 4, 5, 6))
    commitSnapshotFor(attachmentId, cdn = 3)
    coEvery { archiveService.listRemoteMediaObjects(any(), any()) } returns ArchiveError.NetworkError(IOException("boom")).left()

    val result = ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(result.isSuccess).isFalse()
    assertThat(SignalStore.backup.lastCompletedReconciliationSnapshotVersion).isEqualTo(-1L)
  }

  /**
   * A completed crawl proves the object isn't on the CDN, so the row is bookkeeping for something that no longer exists and there is nothing left to orphan.
   */
  @Test
  fun givenOldSnapshotMediaAbsentFromCdn_whenIReconcile_thenIExpectTheRowPruned() {
    val absentMediaId = commitRandomSnapshotEntry()
    commitRandomSnapshotEntry()
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(oldSnapshotMediaIds()).doesNotContain(absentMediaId)
  }

  /**
   * A crawl confirmed this object once, so a later crawl failing to list it is not enough to conclude it's gone. An upload landing mid-crawl produces the same
   * evidence. Pruning here would contradict our own earlier proof and orphan the object.
   */
  @Test
  fun givenOldSnapshotMediaConfirmedByAnEarlierCrawl_whenIReconcile_thenIExpectTheRowKept() {
    val previouslySeenMediaId = commitRandomSnapshotEntry()
    SignalDatabase.backupMediaSnapshots.markSeenOnRemote(listOf(previouslySeenMediaId), 1)
    commitRandomSnapshotEntry()
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(oldSnapshotMediaIds()).contains(previouslySeenMediaId)
  }

  /** The crawl found the object, so this row is the only thing tracking it and must survive even though it left the latest snapshot. */
  @Test
  fun givenOldSnapshotMediaStillOnCdn_whenIReconcile_thenIExpectTheRowKept() {
    val attachmentId = seedFinalizedAttachment("remote-key-kept".toByteArray(), byteArrayOf(3, 1, 4, 1, 5))
    commitSnapshotFor(attachmentId, cdn = 3)
    commitRandomSnapshotEntry()
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(oldSnapshotMediaIds()).contains(mediaIdFor(attachmentId))
  }

  /**
   * The forced-crawl rate limit must only be consumed by a crawl that actually starts, otherwise a job that returns early leaves offloading blocked for a full
   * interval without having verified anything.
   */
  @Test
  fun givenNoBackupUploaded_whenIForceAReconciliation_thenIExpectTheAttemptTimeUnchanged() {
    SignalStore.backup.hasBackupBeenUploaded = false
    SignalStore.backup.lastForcedReconciliationAttemptTime = 0
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.lastForcedReconciliationAttemptTime).isEqualTo(0L)
  }

  @Test
  fun givenACrawlThatStarts_whenIForceAReconciliation_thenIExpectTheAttemptTimeAdvanced() {
    SignalStore.backup.lastForcedReconciliationAttemptTime = 0
    commitRandomSnapshotEntry()
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.lastForcedReconciliationAttemptTime).isGreaterThan(0L)
  }

  /**
   * A crawl with no snapshot to compare against can't confirm anything, so it must not consume the forced-attempt budget. Spending it here would rate-limit the
   * retry that becomes useful once a backup has built a snapshot.
   */
  @Test
  fun givenNoSnapshotYet_whenIForceAReconciliation_thenIExpectTheAttemptTimeUnchanged() {
    SignalStore.backup.lastForcedReconciliationAttemptTime = 0
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.lastForcedReconciliationAttemptTime).isEqualTo(0L)
  }

  /**
   * Same reasoning for the offload gate: a snapshot-less crawl verified nothing, so it must not record a completed version that would let us start deleting
   * local copies of media.
   */
  @Test
  fun givenNoSnapshotYet_whenIReconcile_thenIExpectNoCompletedSnapshotVersionRecorded() {
    SignalStore.backup.lastCompletedReconciliationSnapshotVersion = -1
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(SignalStore.backup.lastCompletedReconciliationSnapshotVersion).isEqualTo(-1L)
  }

  // TODO [cody] return after fixing perf problem of restoring thumbnail state
  // Commented out alongside the skipped thumbnail promotion. See the TODO in ArchiveAttachmentReconciliationJob.syncCdnPage.
  //
  // /**
  //  * A restore sets the full-size state from the backup's CDN claim but leaves the thumbnail state NONE, so the listing is the only thing that can promote it.
  //  * Until it does, the thumbnail is dropped from the snapshot on every backup and gets needlessly re-uploaded once the media is downloaded.
  //  */
  // @Test
  // fun givenRestoredMediaWhoseThumbnailIsOnTheCdn_whenIReconcile_thenIExpectTheThumbnailMarkedFinished() {
  //   val attachmentId = seedArchivedAttachment()
  //   assertThat(SignalDatabase.attachments.getArchiveThumbnailTransferState(attachmentId)).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  //
  //   fakeCdnContainsThumbnail(attachmentId, cdn = 3)
  //
  //   ArchiveAttachmentReconciliationJob(forced = true).run()
  //
  //   assertThat(SignalDatabase.attachments.getArchiveThumbnailTransferState(attachmentId)).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  // }
  //
  // @Test
  // fun givenRestoredMediaWhoseThumbnailIsNotOnTheCdn_whenIReconcile_thenIExpectTheThumbnailLeftAlone() {
  //   val attachmentId = seedArchivedAttachment()
  //   fakeCdnEmpty()
  //
  //   ArchiveAttachmentReconciliationJob(forced = true).run()
  //
  //   assertThat(SignalDatabase.attachments.getArchiveThumbnailTransferState(attachmentId)).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  // }

  // private fun fakeCdnContainsThumbnail(attachmentId: AttachmentId, cdn: Int) {
  //   val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
  //   val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
  //   val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()
  //   val mediaId = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()
  //
  //   coEvery { archiveService.listRemoteMediaObjects(any(), any()) } returns ArchiveApiV2.MediaItemsPage(
  //     storedMediaObjects = listOf(ArchiveApiV2.StoredMediaObject(cdn = cdn, mediaId = mediaId, objectLength = attachment.size)),
  //     cursor = null
  //   ).right()
  // }

  private fun oldSnapshotMediaIds(): List<String> {
    return SignalDatabase.backupMediaSnapshots.getPageOfOldMediaEntries(pageSize = 1_000).map { it.mediaId }
  }

  private fun mediaIdFor(attachmentId: AttachmentId): String {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()

    return MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()
  }

  /**
   * Committing a second entry is what pushes any previously committed row below MAX(snapshot_version), which is the "fell out of the latest snapshot" state.
   */
  private fun commitRandomSnapshotEntry(): String {
    val plaintextHash = Random.nextBytes(32)
    val remoteKey = Random.nextBytes(32)
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()

    SignalDatabase.backupMediaSnapshots.writePendingMediaEntries(
      listOf(MediaEntry(mediaId = mediaId, cdn = 3, plaintextHash = plaintextHash, remoteKey = remoteKey, isThumbnail = false))
    )
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    return mediaId
  }

  private fun seedArchivedAttachment(): AttachmentId {
    val attachment = ArchivedAttachment(
      contentType = MediaUtil.IMAGE_JPEG,
      size = 1024,
      cdn = 3,
      uploadTimestamp = 0,
      key = Random.nextBytes(8),
      cdnKey = "password",
      archiveCdn = 3,
      plaintextHash = Random.nextBytes(8),
      incrementalMac = Random.nextBytes(8),
      incrementalMacChunkSize = 8,
      width = 100,
      height = 100,
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

    val messageId = SignalDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = 0.days, attachment = attachment)).get().messageId
    return SignalDatabase.attachments.getAttachmentsForMessage(messageId).first().attachmentId
  }

  /**
   * [JobTracker] dispatches to listeners on its own executor, so an enqueue that already happened may not have been reported yet.
   */
  private fun awaitEnqueuedJob(factoryKey: String, timeout: Duration = 5.seconds): Boolean {
    val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds

    while (System.currentTimeMillis() < deadline && !enqueuedJobKeys.contains(factoryKey)) {
      Thread.sleep(25)
    }

    return enqueuedJobKeys.contains(factoryKey)
  }

  private fun seedFinalizedAttachment(remoteKey: ByteArray, data: ByteArray, receivedAt: Duration = 0.days): AttachmentId {
    val attachment = createAttachmentPointer(remoteKey, data.size)
    val messageResult = SignalDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = receivedAt, attachment = attachment)).get()
    val attachmentId = messageResult.insertedAttachments!![attachment]!!
    SignalDatabase.attachments.setTransferState(messageResult.messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageResult.messageId, attachmentId, ByteArrayInputStream(data))
    return attachmentId
  }

  private fun commitSnapshotFor(attachmentId: AttachmentId, cdn: Int) {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()

    SignalDatabase.backupMediaSnapshots.writePendingMediaEntries(
      listOf(MediaEntry(mediaId = mediaId, cdn = cdn, plaintextHash = plaintextHash, remoteKey = remoteKey, isThumbnail = false))
    )
    SignalDatabase.backupMediaSnapshots.commitPendingRows()
  }

  private fun fakeCdnContains(attachmentId: AttachmentId, cdn: Int) {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode()

    coEvery { archiveService.listRemoteMediaObjects(any(), any()) } returns ArchiveApiV2.MediaItemsPage(
      storedMediaObjects = listOf(ArchiveApiV2.StoredMediaObject(cdn = cdn, mediaId = mediaId, objectLength = attachment.size)),
      cursor = null
    ).right()
  }

  private fun fakeCdnEmpty() {
    coEvery { archiveService.listRemoteMediaObjects(any(), any()) } returns ArchiveApiV2.MediaItemsPage(storedMediaObjects = emptyList(), cursor = null).right()
  }

  private fun createIncomingMessage(serverTime: Duration, attachment: Attachment): IncomingMessage {
    return IncomingMessage(
      type = MessageType.NORMAL,
      from = harness.others[0],
      body = null,
      sentTimeMillis = serverTime.inWholeMilliseconds,
      serverTimeMillis = serverTime.inWholeMilliseconds,
      receivedTimeMillis = serverTime.inWholeMilliseconds,
      attachments = listOf(attachment)
    )
  }

  private fun createAttachmentPointer(key: ByteArray, size: Int): Attachment {
    return PointerAttachment.forPointer(
      pointer = Optional.of(
        SignalServiceAttachmentPointer(
          cdnNumber = 3,
          remoteId = SignalServiceAttachmentRemoteId.V4("asdf"),
          contentType = MediaUtil.IMAGE_JPEG,
          key = key,
          size = Optional.of(size),
          preview = Optional.empty(),
          width = 2,
          height = 2,
          digest = Optional.of(byteArrayOf()),
          incrementalDigest = Optional.empty(),
          incrementalMacChunkSize = 0,
          fileName = Optional.of("file.jpg"),
          voiceNote = false,
          isBorderless = false,
          isGif = false,
          caption = Optional.empty(),
          blurHash = Optional.empty(),
          uploadTimestamp = 0,
          uuid = null
        )
      )
    ).get()
  }
}

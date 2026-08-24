/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.backup.MediaName
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.MediaEntry
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.io.ByteArrayInputStream
import java.util.Optional
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class ArchiveCommitAttachmentDeletesJobTest {

  @get:Rule
  val harness = SignalActivityRule()

  private val deletedFromCdn = slot<Set<ArchivedMediaObject>>()

  private val jobPageSize = ArchiveCommitAttachmentDeletesJob.REMOTE_DELETE_BATCH_SIZE

  @Before
  fun setUp() {
    SignalStore.backup.backupTier = MessageBackupTier.PAID
    SignalStore.backup.lastUsedMessageCutoffTime = 0

    mockkObject(ArchiveCommitAttachmentDeletesJob)
    coEvery { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), capture(deletedFromCdn), any(), any()) } returns null
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  /**
   * Media only leaves the snapshot because the backup stopped referencing it, which a bookkeeping bug can cause while the attachment is still very much present.
   * Deleting on that basis alone is how the only remaining copy of media gets destroyed, so the pass has to discriminate within a page rather than trusting the
   * snapshot.
   */
  @Test
  fun givenOldSnapshotMediaBothReferencedAndOrphaned_whenIRun_thenIExpectOnlyTheOrphanDeletedFromTheCdn() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(1, 2, 3, 4, 5))
    val referenced = entryFor(attachmentId)
    val orphan = randomEntry()

    commit(referenced, orphan)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    val deletedMediaIds = deletedFromCdn.captured.map { it.mediaId }
    assertThat(deletedMediaIds).contains(orphan.mediaId)
    assertThat(deletedMediaIds).doesNotContain(referenced.mediaId)
  }

  @Test
  fun givenOnlyStillReferencedOldSnapshotMedia_whenIRun_thenIExpectNothingDeletedFromTheCdn() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(9, 8, 7, 6, 5))

    commit(entryFor(attachmentId))
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * A retained row is the only record that its CDN object exists, so pruning it would orphan the object permanently. The run still has to terminate, which it
   * does by paging past retained rows by id.
   */
  @Test
  fun givenOnlyStillReferencedOldSnapshotMedia_whenIRun_thenIExpectTheSnapshotRowsRetained() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(4, 4, 4, 4, 4))
    val referenced = entryFor(attachmentId)

    commit(referenced)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(referenced.mediaId)
  }

  @Test
  fun givenOldSnapshotMediaWithNoAttachment_whenIRun_thenIExpectTheSnapshotRowPruned() {
    val orphan = randomEntry()

    commit(orphan)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).doesNotContain(orphan.mediaId)
  }

  /**
   * Coverage across pages: if the loop only ever handled the first page, the overflow rows would stay on the CDN forever with nothing left to reconsider them.
   */
  @Test
  fun givenMoreOrphansThanOnePage_whenIRun_thenIExpectEveryPageProcessed() {
    val orphans = List(jobPageSize + 1) { randomEntry() }

    commit(orphans)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).isEmpty()
  }

  /**
   * Termination when a whole page is retained. Nothing is removed from the table here, so the loop can only end by advancing its cursor past rows it declined to
   * delete. If it paged from the start each time it would spin forever and this test would hang rather than fail.
   */
  @Test
  fun givenMoreUndeletableRowsThanOnePage_whenIRun_thenIExpectItToTerminateAndRetainThem() {
    val unknownCdn = List(jobPageSize + 1) { randomEntry(cdn = null) }

    commit(unknownCdn)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).hasSize(unknownCdn.size)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * Users over the backup size limit get old messages left out of the export entirely, so nothing in the backup references their media any more and it should
   * stop occupying paid archive quota. Before this, an attachment row existing was enough to retain it forever.
   */
  @Test
  fun givenMediaWhoseMessagePredatesTheMessageCutoff_whenIRun_thenIExpectItDeletedFromTheCdn() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(1, 1, 2, 3, 5), receivedAt = 10.days)
    val agedOut = entryFor(attachmentId)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds

    commit(agedOut)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(deletedFromCdn.captured.map { it.mediaId }).contains(agedOut.mediaId)
  }

  /**
   * The exporter includes messages on `date_received >= cutoff`, so one landing exactly on the threshold is still in the backup. Deleting it would take media
   * the backup still references.
   */
  @Test
  fun givenMediaWhoseMessageIsExactlyAtTheMessageCutoff_whenIRun_thenIExpectItRetained() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(2, 2, 2, 2, 2), receivedAt = 30.days)
    val boundary = entryFor(attachmentId)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds

    commit(boundary)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(boundary.mediaId)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * A wallpaper has no message row at all. Phrasing the cutoff as "its message is recent enough" would drop wallpapers out of the referent set and delete media
   * the user still has applied, so the check only disqualifies an attachment whose message exists and is too old.
   */
  @Test
  fun givenAWallpaperAndAMessageCutoff_whenIRun_thenIExpectItRetained() {
    val attachmentId = SignalDatabase.attachments.insertWallpaper(ByteArrayInputStream(byteArrayOf(7, 7, 7, 7, 7)))
    val wallpaper = entryFor(attachmentId)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds

    commit(wallpaper)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(wallpaper.mediaId)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * Cutting off messages only happens for backups over the size limit, which is vanishingly rare, so the ordinary case has to behave exactly as it did before.
   */
  @Test
  fun givenNoMessageCutoff_whenIRun_thenIExpectAncientMediaRetained() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(3, 3, 3, 3, 3), receivedAt = 0.days)
    val ancient = entryFor(attachmentId)

    commit(ancient)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(ancient.mediaId)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * Media is keyed by hash and remote key, so the same bytes can hang off several messages. One of them surviving the cutoff means the backup still references
   * the media, and it has to be judged referenced rather than per-row.
   */
  @Test
  fun givenMediaReferencedByBothAnAgedOutAndACurrentMessage_whenIRun_thenIExpectItRetained() {
    val data = byteArrayOf(4, 5, 6, 7, 8)
    val agedOutId = seedFinalizedAttachment(data, receivedAt = 10.days)
    val currentId = seedFinalizedAttachment(data, receivedAt = 40.days)

    val shared = entryFor(agedOutId)
    assertThat(entryFor(currentId).mediaId).isEqualTo(shared.mediaId)

    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds

    commit(shared)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(shared.mediaId)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * Full-size and thumbnail media share a hash and key but are judged by different rules, so a thumbnail can be reclaimable while its full-size copy is not.
   */
  @Test
  fun givenAThumbnailWhoseAttachmentStillWantsIt_whenIRun_thenIExpectItRetained() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(5, 1, 5, 1, 5))
    val thumbnail = entryFor(attachmentId, isThumbnail = true)

    commit(thumbnail)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(remainingOldMediaIds()).contains(thumbnail.mediaId)
    coVerify(exactly = 0) { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) }
  }

  /**
   * Wallpapers never get a thumbnail written into the snapshot, so one already on the CDN is unreachable bookkeeping. Its full-size object still has a referent
   * and has to survive, which is what makes this a thumbnail-only reclaim rather than a blanket delete.
   */
  @Test
  fun givenAWallpaperThumbnail_whenIRun_thenIExpectOnlyTheThumbnailDeletedFromTheCdn() {
    val attachmentId = SignalDatabase.attachments.insertWallpaper(ByteArrayInputStream(byteArrayOf(6, 2, 6, 2, 6)))
    val thumbnail = entryFor(attachmentId, isThumbnail = true)
    val fullSize = entryFor(attachmentId)

    commit(thumbnail, fullSize)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    val deletedMediaIds = deletedFromCdn.captured.map { it.mediaId }
    assertThat(deletedMediaIds).contains(thumbnail.mediaId)
    assertThat(deletedMediaIds).doesNotContain(fullSize.mediaId)
  }

  /**
   * The message cutoff has to reach the thumbnail path too, otherwise media dropped from the backup keeps half of its CDN footprint.
   */
  @Test
  fun givenAThumbnailWhoseMessagePredatesTheMessageCutoff_whenIRun_thenIExpectItDeletedFromTheCdn() {
    val attachmentId = seedFinalizedAttachment(byteArrayOf(7, 3, 7, 3, 7), receivedAt = 10.days)
    val thumbnail = entryFor(attachmentId, isThumbnail = true)
    SignalStore.backup.lastUsedMessageCutoffTime = 30.days.inWholeMilliseconds

    commit(thumbnail)
    commit(randomEntry())

    ArchiveCommitAttachmentDeletesJob().run()

    assertThat(deletedFromCdn.captured.map { it.mediaId }).contains(thumbnail.mediaId)
  }

  private fun entryFor(attachmentId: AttachmentId, isThumbnail: Boolean = false): MediaEntry {
    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()

    val mediaName = if (isThumbnail) {
      MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey)
    } else {
      MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey)
    }

    return MediaEntry(
      mediaId = mediaName.toMediaId(SignalStore.backup.mediaRootBackupKey).encode(),
      cdn = 3,
      plaintextHash = plaintextHash,
      remoteKey = remoteKey,
      isThumbnail = isThumbnail
    )
  }

  private fun randomEntry(cdn: Int? = 3): MediaEntry {
    val plaintextHash = Random.nextBytes(32)
    val remoteKey = Random.nextBytes(32)

    return MediaEntry(
      mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).encode(),
      cdn = cdn,
      plaintextHash = plaintextHash,
      remoteKey = remoteKey,
      isThumbnail = false
    )
  }

  private fun commit(vararg entries: MediaEntry) {
    commit(entries.toList())
  }

  private fun commit(entries: List<MediaEntry>) {
    SignalDatabase.backupMediaSnapshots.writePendingMediaEntries(entries)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()
  }

  private fun remainingOldMediaIds(): List<String> {
    return SignalDatabase.backupMediaSnapshots.getPageOfOldMediaEntries(pageSize = (jobPageSize * 3)).map { it.mediaId }
  }

  private fun seedFinalizedAttachment(data: ByteArray, receivedAt: Duration = 0.days): AttachmentId {
    val attachment = createAttachmentPointer(Random.nextBytes(32), data.size)
    val messageResult = SignalDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = receivedAt, attachment = attachment)).get()
    val attachmentId = messageResult.insertedAttachments!![attachment]!!
    SignalDatabase.attachments.setTransferState(messageResult.messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)
    SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageResult.messageId, attachmentId, ByteArrayInputStream(data))
    return attachmentId
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

package org.thoughtcrime.securesms.database

import androidx.media3.common.util.Util
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.count
import org.signal.core.util.readToSingleInt
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable.ArchiveMediaItem
import org.thoughtcrime.securesms.testing.SignalActivityRule

@RunWith(AndroidJUnit4::class)
class BackupMediaSnapshotTableTest {

  companion object {
    private const val SEQUENCE_COUNT = 100
    private const val SEQUENCE_COUNT_WITH_THUMBNAILS = 200
  }

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun givenAnEmptyTable_whenIWriteToTable_thenIExpectEmptyTable() {
    val pendingSyncTime = 1L
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(), pendingSyncTime)

    val count = getSyncedItemCount(pendingSyncTime)

    assertThat(count).isEqualTo(0)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommit_thenIExpectFilledTable() {
    val pendingSyncTime = 1L
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(), pendingSyncTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getSyncedItemCount(pendingSyncTime)

    assertThat(count).isEqualTo(SEQUENCE_COUNT_WITH_THUMBNAILS)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIds_thenIExpectUncommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    val newObjectCountWithThumbnails = newObjectCount * 2
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(newObjectCount), newPendingTime)

    val count = SignalDatabase.backupMediaSnapshots.readableDatabase.count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.LAST_SYNC_TIME} = 1 AND ${BackupMediaSnapshotTable.PENDING_SYNC_TIME} = $newPendingTime")
      .run()
      .readToSingleInt(-1)

    assertThat(count).isEqualTo(newObjectCountWithThumbnails)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIdsAndCommit_thenIExpectCommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    val newObjectCountWithThumbnails = newObjectCount * 2
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(newObjectCount), newPendingTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = SignalDatabase.backupMediaSnapshots.readableDatabase.count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.LAST_SYNC_TIME} = $newPendingTime AND ${BackupMediaSnapshotTable.PENDING_SYNC_TIME} = $newPendingTime")
      .run()
      .readToSingleInt(-1)

    val total = getTotalItemCount()

    assertThat(count).isEqualTo(newObjectCountWithThumbnails)
    assertThat(total).isEqualTo(SEQUENCE_COUNT_WITH_THUMBNAILS)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIdsAndCommitThenDelete_thenIExpectOnlyCommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    val newObjectCountWithThumbnails = newObjectCount * 2
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveMediaItemSequence(newObjectCount), newPendingTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val page = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(currentSyncTime = newPendingTime, pageSize = 100)
    SignalDatabase.backupMediaSnapshots.deleteMediaObjects(page)

    val total = getTotalItemCount()

    assertThat(total).isEqualTo(newObjectCountWithThumbnails)
  }

  @Test
  fun getMediaObjectsWithNonMatchingCdn_noMismatches() {
    val localData = listOf(
      createArchiveMediaItem(seed = 1, cdn = 1),
      createArchiveMediaItem(seed = 2, cdn = 2)
    )

    val remoteData = listOf(
      createArchiveMediaObject(seed = 1, cdn = 1),
      createArchiveMediaObject(seed = 2, cdn = 2)
    )

    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(localData.asSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val mismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(remoteData)
    assertThat(mismatches.size).isEqualTo(0)
  }

  @Test
  fun getMediaObjectsWithNonMatchingCdn_oneMismatch() {
    val localData = listOf(
      createArchiveMediaItem(seed = 1, cdn = 1),
      createArchiveMediaItem(seed = 2, cdn = 2)
    )

    val remoteData = listOf(
      createArchiveMediaObject(seed = 1, cdn = 1),
      createArchiveMediaObject(seed = 2, cdn = 99)
    )

    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(localData.asSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val mismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(remoteData)
    assertThat(mismatches.size).isEqualTo(1)
    assertThat(mismatches.get(0).cdn).isEqualTo(99)
    assertThat(mismatches.get(0).digest).isEqualTo(localData.get(1).digest)
  }

  @Test
  fun getMediaObjectsThatCantBeFound_allFound() {
    val localData = listOf(
      createArchiveMediaItem(seed = 1, cdn = 1),
      createArchiveMediaItem(seed = 2, cdn = 2)
    )

    val remoteData = listOf(
      createArchiveMediaObject(seed = 1, cdn = 1),
      createArchiveMediaObject(seed = 2, cdn = 2)
    )

    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(localData.asSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val notFound = SignalDatabase.backupMediaSnapshots.getMediaObjectsThatCantBeFound(remoteData)
    assertThat(notFound.size).isEqualTo(0)
  }

  @Test
  fun getMediaObjectsThatCantBeFound_oneMissing() {
    val localData = listOf(
      createArchiveMediaItem(seed = 1, cdn = 1),
      createArchiveMediaItem(seed = 2, cdn = 2)
    )

    val remoteData = listOf(
      createArchiveMediaObject(seed = 1, cdn = 1),
      createArchiveMediaObject(seed = 3, cdn = 2)
    )

    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(localData.asSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val notFound = SignalDatabase.backupMediaSnapshots.getMediaObjectsThatCantBeFound(remoteData)
    assertThat(notFound.size).isEqualTo(1)
    assertThat(notFound.first().cdn).isEqualTo(2)
  }

  private fun getTotalItemCount(): Int {
    return SignalDatabase.backupMediaSnapshots.readableDatabase.count().from(BackupMediaSnapshotTable.TABLE_NAME).run().readToSingleInt(-1)
  }

  private fun getSyncedItemCount(pendingTime: Long): Int {
    return SignalDatabase.backupMediaSnapshots.readableDatabase.count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.LAST_SYNC_TIME} = $pendingTime AND ${BackupMediaSnapshotTable.PENDING_SYNC_TIME} = $pendingTime")
      .run()
      .readToSingleInt(-1)
  }

  private fun generateArchiveMediaItemSequence(count: Int = SEQUENCE_COUNT): Sequence<ArchiveMediaItem> {
    return generateSequence(0) { seed -> if (seed < (count - 1)) seed + 1 else null }
      .map { createArchiveMediaItem(it) }
  }

  private fun createArchiveMediaItem(seed: Int, cdn: Int = 0): ArchiveMediaItem {
    return ArchiveMediaItem(
      mediaId = "media_id_$seed",
      thumbnailMediaId = "thumbnail_media_id_$seed",
      cdn = cdn,
      digest = Util.toByteArray(seed)
    )
  }

  private fun createArchiveMediaObject(seed: Int, cdn: Int = 0): ArchivedMediaObject {
    return ArchivedMediaObject(
      mediaId = "media_id_$seed",
      cdn = cdn
    )
  }
}

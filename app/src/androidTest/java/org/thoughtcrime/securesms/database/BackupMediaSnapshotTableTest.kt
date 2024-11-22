package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.count
import org.signal.core.util.readToSingleInt
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs

@RunWith(AndroidJUnit4::class)
class BackupMediaSnapshotTableTest {

  companion object {
    private const val SEQUENCE_COUNT = 100
  }

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun givenAnEmptyTable_whenIWriteToTable_thenIExpectEmptyTable() {
    val pendingSyncTime = 1L
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(), pendingSyncTime)

    val count = getSyncedItemCount(pendingSyncTime)

    count.assertIs(0)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommit_thenIExpectFilledTable() {
    val pendingSyncTime = 1L
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(), pendingSyncTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getSyncedItemCount(pendingSyncTime)

    count.assertIs(SEQUENCE_COUNT)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIds_thenIExpectUncommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(newObjectCount), newPendingTime)

    val count = SignalDatabase.backupMediaSnapshots.readableDatabase.count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.LAST_SYNC_TIME} = 1 AND ${BackupMediaSnapshotTable.PENDING_SYNC_TIME} = $newPendingTime")
      .run()
      .readToSingleInt(-1)

    count.assertIs(50)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIdsAndCommit_thenIExpectCommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(newObjectCount), newPendingTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = SignalDatabase.backupMediaSnapshots.readableDatabase.count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.LAST_SYNC_TIME} = $newPendingTime AND ${BackupMediaSnapshotTable.PENDING_SYNC_TIME} = $newPendingTime")
      .run()
      .readToSingleInt(-1)

    val total = getTotalItemCount()

    count.assertIs(50)
    total.assertIs(SEQUENCE_COUNT)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIdsAndCommitThenDelete_thenIExpectOnlyCommittedOverrides() {
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(), 1L)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val newPendingTime = 2L
    val newObjectCount = 50
    SignalDatabase.backupMediaSnapshots.writePendingMediaObjects(generateArchiveObjectSequence(newObjectCount), newPendingTime)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val page = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(currentSyncTime = newPendingTime, pageSize = 100)
    SignalDatabase.backupMediaSnapshots.deleteMediaObjects(page)

    val total = getTotalItemCount()

    total.assertIs(50)
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

  private fun generateArchiveObjectSequence(count: Int = SEQUENCE_COUNT): Sequence<ArchivedMediaObject> {
    return generateSequence(0) { seed -> if (seed < (count - 1)) seed + 1 else null }
      .map { ArchivedMediaObject(mediaId = "media_id_$it", 0) }
  }
}

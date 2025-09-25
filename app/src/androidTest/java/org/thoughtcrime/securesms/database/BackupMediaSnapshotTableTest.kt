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

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun givenAnEmptyTable_whenIWriteToTable_thenIExpectEmptyTable() {
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = 100))

    val count = getCountForLatestSnapshot(includeThumbnails = true)

    assertThat(count).isEqualTo(0)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommit_thenIExpectFilledTable() {
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = 100))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getCountForLatestSnapshot(includeThumbnails = false)

    assertThat(count).isEqualTo(100)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommit_thenIExpectFilledTableWithThumbnails() {
    val inputCount = 100
    val countWithThumbnails = inputCount * 2

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = inputCount))
    SignalDatabase.backupMediaSnapshots.writeThumbnailPendingMediaObjects(generateArchiveMediaItemSequence(count = inputCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getCountForLatestSnapshot(includeThumbnails = true)

    assertThat(count).isEqualTo(countWithThumbnails)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommitQuotes_thenIExpectFilledTableWithNoThumbnails() {
    val inputCount = 100

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = inputCount, quote = true))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getCountForLatestSnapshot(includeThumbnails = true)

    assertThat(count).isEqualTo(inputCount)
  }

  @Test
  fun givenAnEmptyTable_whenIWriteToTableAndCommitNonMedia_thenIExpectFilledTableWithNoThumbnails() {
    val inputCount = 100

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = inputCount, contentType = "text/plain"))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val count = getCountForLatestSnapshot(includeThumbnails = true)

    assertThat(count).isEqualTo(inputCount)
  }

  @Test
  fun givenAFilledTable_whenIReinsertObjects_thenIExpectUncommittedOverrides() {
    val initialCount = 100
    val additionalCount = 25

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = initialCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    // This relies on how the sequence of mediaIds is generated in tests -- the ones we generate here will have the mediaIds as the ones we generated above
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = additionalCount))

    val pendingCount = getCountForPending(includeThumbnails = false)
    val latestVersionCount = getCountForLatestSnapshot(includeThumbnails = false)

    assertThat(pendingCount).isEqualTo(additionalCount)
    assertThat(latestVersionCount).isEqualTo(initialCount)
  }

  @Test
  fun givenAFilledTable_whenIReinsertObjectsAndCommit_thenIExpectCommittedOverrides() {
    val initialCount = 100
    val additionalCount = 25

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = initialCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    // This relies on how the sequence of mediaIds is generated in tests -- the ones we generate here will have the mediaIds as the ones we generated above
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = additionalCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val pendingCount = getCountForPending(includeThumbnails = false)
    val latestVersionCount = getCountForLatestSnapshot(includeThumbnails = false)
    val totalCount = getTotalItemCount(includeThumbnails = false)

    assertThat(pendingCount).isEqualTo(0)
    assertThat(latestVersionCount).isEqualTo(additionalCount)
    assertThat(totalCount).isEqualTo(initialCount)
  }

  @Test
  fun givenAFilledTable_whenIInsertSimilarIdsAndCommitThenDelete_thenIExpectOnlyCommittedOverrides() {
    val initialCount = 100
    val additionalCount = 25

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = initialCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = additionalCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val page = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(pageSize = 1_000)
    SignalDatabase.backupMediaSnapshots.deleteOldMediaObjects(page)

    val total = getTotalItemCount(includeThumbnails = false)

    assertThat(total).isEqualTo(additionalCount)
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

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(localData.asSequence())
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

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(localData.asSequence())
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val mismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(remoteData)
    assertThat(mismatches.size).isEqualTo(1)
    assertThat(mismatches[0].cdn).isEqualTo(99)
    assertThat(mismatches[0].plaintextHash).isEqualTo(localData[1].plaintextHash)
    assertThat(mismatches[0].remoteKey).isEqualTo(localData[1].remoteKey)
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

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(localData.asSequence())
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

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(localData.asSequence())
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val notFound = SignalDatabase.backupMediaSnapshots.getMediaObjectsThatCantBeFound(remoteData)
    assertThat(notFound.size).isEqualTo(1)
    assertThat(notFound.first()).isEqualTo(remoteData[1])
  }

  @Test
  fun getCurrentSnapshotVersion_emptyTable() {
    val version = SignalDatabase.backupMediaSnapshots.getCurrentSnapshotVersion()

    assertThat(version).isEqualTo(0)
  }

  @Test
  fun getCurrentSnapshotVersion_singleCommit() {
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = 100))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val version = SignalDatabase.backupMediaSnapshots.getCurrentSnapshotVersion()

    assertThat(version).isEqualTo(1)
  }

  @Test
  fun getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion_noneMarkedSeen() {
    val initialCount = 100

    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(generateArchiveMediaItemSequence(count = initialCount))
    SignalDatabase.backupMediaSnapshots.writeThumbnailPendingMediaObjects(generateArchiveMediaItemSequence(count = initialCount))
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val notSeenCount = SignalDatabase.backupMediaSnapshots.getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion(1).count

    val expectedOldCountIncludingThumbnails = initialCount * 2

    assertThat(notSeenCount).isEqualTo(expectedOldCountIncludingThumbnails)
  }

  @Test
  fun getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion_someMarkedSeen() {
    val initialCount = 100
    val markSeenCount = 25

    val itemsToCommit = generateArchiveMediaItemSequence(count = initialCount)
    SignalDatabase.backupMediaSnapshots.writeFullSizePendingMediaObjects(itemsToCommit)
    SignalDatabase.backupMediaSnapshots.writeThumbnailPendingMediaObjects(itemsToCommit)
    SignalDatabase.backupMediaSnapshots.commitPendingRows()

    val normalIdsToMarkSeen = itemsToCommit.take(markSeenCount).map { it.mediaId }.toList()
    val thumbnailIdsToMarkSeen = itemsToCommit.take(markSeenCount).map { it.thumbnailMediaId }.toList()
    val allItemsToMarkSeen = normalIdsToMarkSeen + thumbnailIdsToMarkSeen

    SignalDatabase.backupMediaSnapshots.markSeenOnRemote(allItemsToMarkSeen, 1)

    val notSeenCount = SignalDatabase.backupMediaSnapshots.getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion(1).count

    val expectedOldCount = initialCount - markSeenCount
    val expectedOldCountIncludingThumbnails = expectedOldCount * 2

    assertThat(notSeenCount).isEqualTo(expectedOldCountIncludingThumbnails)
  }

  private fun getTotalItemCount(includeThumbnails: Boolean): Int {
    return if (includeThumbnails) {
      SignalDatabase.backupMediaSnapshots.readableDatabase
        .count()
        .from(BackupMediaSnapshotTable.TABLE_NAME)
        .run()
        .readToSingleInt(0)
    } else {
      SignalDatabase.backupMediaSnapshots.readableDatabase
        .count()
        .from(BackupMediaSnapshotTable.TABLE_NAME)
        .where("${BackupMediaSnapshotTable.IS_THUMBNAIL} = 0")
        .run()
        .readToSingleInt(0)
    }
  }

  private fun getCountForLatestSnapshot(includeThumbnails: Boolean): Int {
    val thumbnailFilter = if (!includeThumbnails) {
      " AND ${BackupMediaSnapshotTable.IS_THUMBNAIL} = 0"
    } else {
      ""
    }

    return SignalDatabase.backupMediaSnapshots.readableDatabase
      .count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.SNAPSHOT_VERSION} = ${BackupMediaSnapshotTable.MAX_VERSION} AND ${BackupMediaSnapshotTable.SNAPSHOT_VERSION} != ${BackupMediaSnapshotTable.UNKNOWN_VERSION}" + thumbnailFilter)
      .run()
      .readToSingleInt(0)
  }

  private fun getCountForPending(includeThumbnails: Boolean): Int {
    val thumbnailFilter = if (!includeThumbnails) {
      " AND ${BackupMediaSnapshotTable.IS_THUMBNAIL} = 0"
    } else {
      ""
    }

    return SignalDatabase.backupMediaSnapshots.readableDatabase
      .count()
      .from(BackupMediaSnapshotTable.TABLE_NAME)
      .where("${BackupMediaSnapshotTable.IS_PENDING} != 0" + thumbnailFilter)
      .run()
      .readToSingleInt(0)
  }

  private fun generateArchiveMediaItemSequence(count: Int, quote: Boolean = false, contentType: String = "image/jpeg"): Sequence<ArchiveMediaItem> {
    return (1..count)
      .asSequence()
      .map { createArchiveMediaItem(it, quote = quote, contentType = contentType) }
  }

  private fun createArchiveMediaItem(seed: Int, cdn: Int = 0, quote: Boolean = false, contentType: String = "image/jpeg"): ArchiveMediaItem {
    return ArchiveMediaItem(
      mediaId = "media_id_$seed",
      thumbnailMediaId = "thumbnail_media_id_$seed",
      cdn = cdn,
      plaintextHash = Util.toByteArray(seed),
      remoteKey = Util.toByteArray(seed),
      quote = quote,
      contentType = contentType
    )
  }

  private fun createArchiveMediaObject(seed: Int, cdn: Int = 0): ArchivedMediaObject {
    return ArchivedMediaObject(
      mediaId = "media_id_$seed",
      cdn = cdn
    )
  }
}

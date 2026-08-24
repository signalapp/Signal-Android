package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.StickerRecord
import org.signal.core.util.Hex
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.model.IncomingSticker
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.SignalStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import org.thoughtcrime.securesms.database.model.StickerPackRecord as LocalStickerPackRecord
import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord as RemoteStickerPackRecord

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StickerTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val packId1 = Hex.toStringCondensed(ByteArray(16) { 1 })
  private val packId2 = Hex.toStringCondensed(ByteArray(16) { 2 })
  private val packId3 = Hex.toStringCondensed(ByteArray(16) { 3 })
  private val packKey1 = Hex.toStringCondensed(ByteArray(32) { 1 })
  private val packKey2 = Hex.toStringCondensed(ByteArray(32) { 2 })
  private val packKey3 = Hex.toStringCondensed(ByteArray(32) { 3 })

  @Before
  fun setUp() {
    SignalDatabase.stickers.writableDatabase.deleteAll(StickerTables.Sticker.TABLE_NAME)
    SignalDatabase.stickers.writableDatabase.deleteAll(StickerTables.Pack.TABLE_NAME)
  }

  @Test
  fun `given an installed pack, when I get it for storage sync, then I expect a storage id and no tombstone`() {
    installPack(packId1, packKey1)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!

    assertThat(pack.installed).isTrue()
    assertThat(pack.deletedTimestampMs).isEqualTo(0)
    assertThat(pack.storageServiceId).isNotNull()
  }

  @Test
  fun `given two installed packs, when I install them, then I expect increasing positions`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)

    val pack1 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    val pack2 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId2))!!

    assertThat(pack2.position).isEqualTo(pack1.position + 1)
  }

  @Test
  fun `given an installed pack, when I uninstall it, then I expect a tombstone with a rotated storage id`() {
    installPack(packId1, packKey1)
    val originalId = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!.storageServiceId!!

    SignalDatabase.stickers.uninstallPack(packId1)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    assertThat(pack.installed).isFalse()
    assertThat(pack.deletedTimestampMs).isNotEqualTo(0)
    assertThat(pack.position).isEqualTo(0)
    assertThat(pack.storageServiceId).isNotNull()
    assertThat(pack.storageServiceId).isNotEqualTo(originalId)
  }

  @Test
  fun `given a tombstoned pack, when I reinstall it, then I expect the tombstone cleared and the max position`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)
    SignalDatabase.stickers.uninstallPack(packId1)

    SignalDatabase.stickers.markPackAsInstalled(packId1, notify = false)

    val pack1 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    val pack2 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId2))!!
    assertThat(pack1.installed).isTrue()
    assertThat(pack1.deletedTimestampMs).isEqualTo(0)
    assertThat(pack1.position).isEqualTo(pack2.position + 1)
  }

  @Test
  fun `given a remote record, when I insert it locally, then I expect an installed pack with the remote storage id`() {
    val remoteRecord = SignalStickerPackRecord(
      StorageId.forStickerPack(byteArrayOf(1, 2, 3)),
      RemoteStickerPackRecord(
        packId = Hex.fromStringCondensed(packId1).toByteString(),
        packKey = Hex.fromStringCondensed(packKey1).toByteString(),
        position = 7
      )
    )

    SignalDatabase.stickers.insertStickerPackFromStorageSync(remoteRecord)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    assertThat(pack.installed).isTrue()
    assertThat(pack.position).isEqualTo(7)
    assertThat(pack.packKey.value).isEqualTo(packKey1)
    assertThat(pack.storageServiceId).isEqualTo(remoteRecord.id)
  }

  @Test
  fun `given a deleted remote record, when I insert it locally, then I expect a tombstone`() {
    val remoteRecord = SignalStickerPackRecord(
      StorageId.forStickerPack(byteArrayOf(1, 2, 3)),
      RemoteStickerPackRecord(
        packId = Hex.fromStringCondensed(packId1).toByteString(),
        deletedAtTimestamp = 1000L
      )
    )

    SignalDatabase.stickers.insertStickerPackFromStorageSync(remoteRecord)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    assertThat(pack.installed).isFalse()
    assertThat(pack.deletedTimestampMs).isEqualTo(1000L)
    assertThat(pack.storageServiceId).isEqualTo(remoteRecord.id)
  }

  @Test
  fun `given an installed pack, when I apply a deleted remote record, then I expect it to be uninstalled`() {
    installPack(packId1, packKey1)

    val remoteRecord = SignalStickerPackRecord(
      StorageId.forStickerPack(byteArrayOf(1, 2, 3)),
      RemoteStickerPackRecord(
        packId = Hex.fromStringCondensed(packId1).toByteString(),
        deletedAtTimestamp = 1000L
      )
    )

    SignalDatabase.stickers.updateStickerPackFromStorageSync(remoteRecord)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    assertThat(pack.installed).isFalse()
    assertThat(pack.deletedTimestampMs).isEqualTo(1000L)
    assertThat(pack.packKey.value).isEqualTo(packKey1)
    assertThat(pack.storageServiceId).isEqualTo(remoteRecord.id)
  }

  @Test
  fun `given a tombstoned pack, when I apply an active remote record, then I expect it to be reinstalled`() {
    installPack(packId1, packKey1)
    SignalDatabase.stickers.uninstallPack(packId1)

    val remoteRecord = SignalStickerPackRecord(
      StorageId.forStickerPack(byteArrayOf(1, 2, 3)),
      RemoteStickerPackRecord(
        packId = Hex.fromStringCondensed(packId1).toByteString(),
        packKey = Hex.fromStringCondensed(packKey1).toByteString(),
        position = 5
      )
    )

    SignalDatabase.stickers.updateStickerPackFromStorageSync(remoteRecord)

    val pack = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    assertThat(pack.installed).isTrue()
    assertThat(pack.deletedTimestampMs).isEqualTo(0)
    assertThat(pack.position).isEqualTo(5)
    assertThat(pack.storageServiceId).isEqualTo(remoteRecord.id)
  }

  @Test
  fun `given installed packs, when I update their storage sync ids, then I expect an updated map`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)

    val existingMap = SignalDatabase.stickers.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      SignalDatabase.stickers.applyStorageIdUpdate(id, StorageId.forStickerPack(StorageSyncHelper.generateKey()))
    }
    val updatedMap = SignalDatabase.stickers.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertThat(updatedMap[id]).isNotEqualTo(storageId)
    }
  }

  @Test
  fun `given a pack deleted longer than the message queue time, when I clean up, then I expect it to not have a storage id`() {
    installPack(packId1, packKey1)
    SignalDatabase.stickers.uninstallPack(packId1)

    SignalDatabase.stickers.removeStorageIdsFromOldDeletedPacks(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))

    assertThat(SignalDatabase.stickers.getStorageSyncIds()).isEmpty()
    assertThat(SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!.storageServiceId).isNull()
  }

  @Test
  fun `given installed packs, when I update the pack positions, then I expect display-order positions and rotated ids`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)
    installPack(packId3, packKey3)
    val originalIds = SignalDatabase.stickers.getStorageSyncIdsMap()

    SignalDatabase.stickers.updatePackPositions(
      listOf(
        localRecord(packId3, packKey3),
        localRecord(packId1, packKey1),
        localRecord(packId2, packKey2)
      )
    )

    val pack1 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!
    val pack2 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId2))!!
    val pack3 = SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId3))!!

    assertThat(pack3.position).isEqualTo(0)
    assertThat(pack1.position).isEqualTo(1)
    assertThat(pack2.position).isEqualTo(2)

    val updatedIds = SignalDatabase.stickers.getStorageSyncIdsMap()
    originalIds.forEach { (id, storageId) ->
      assertThat(updatedIds[id]).isNotEqualTo(storageId)
    }
  }

  @Test
  fun `given installed packs, when I get them, then I expect oldest first by ascending position`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)
    installPack(packId3, packKey3)

    assertThat(installedPackIds()).isEqualTo(listOf(packId1, packId2, packId3))
  }

  @Test
  fun `given reordered packs, when I get them, then I expect the requested display order`() {
    installPack(packId1, packKey1)
    installPack(packId2, packKey2)
    installPack(packId3, packKey3)

    SignalDatabase.stickers.updatePackPositions(
      listOf(
        localRecord(packId1, packKey1),
        localRecord(packId3, packKey3),
        localRecord(packId2, packKey2)
      )
    )

    assertThat(installedPackIds()).isEqualTo(listOf(packId1, packId3, packId2))
  }

  private fun installedPackIds(): List<String> {
    return StickerTables.StickerPackRecordReader(SignalDatabase.stickers.getInstalledStickerPacks()).use { reader ->
      reader.asSequence().map { it.packId }.toList()
    }
  }

  private fun installPack(packId: String, packKey: String) {
    SignalDatabase.stickers.insertSticker(
      sticker = IncomingSticker(
        packId = packId,
        packKey = packKey,
        packTitle = "Title",
        packAuthor = "Author",
        stickerId = 0,
        emoji = "",
        contentType = "image/webp",
        isCover = true,
        isInstalled = true
      ),
      dataStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
      notify = false
    )
  }

  private fun localRecord(packId: String, packKey: String): LocalStickerPackRecord {
    return LocalStickerPackRecord(
      packId = packId,
      packKey = packKey,
      title = "Title",
      author = "Author",
      cover = StickerRecord(rowId = 1, packId = packId, packKey = packKey, stickerId = 0, emoji = "", contentType = "image/webp", size = 4, isCover = true),
      isInstalled = true
    )
  }
}

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.squareup.wire.FieldEncoding
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.FavoriteStickerSyncRecord
import org.signal.core.models.database.StickerRecord
import org.signal.core.util.Hex
import org.signal.core.util.deleteAll
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.model.IncomingSticker
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.SignalFavoriteStickerRecord
import org.whispersystems.signalservice.api.storage.SignalStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.FavoriteStickerRecord
import java.io.ByteArrayInputStream
import java.io.File
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

  @Test
  fun `given a sticker with no emoji, when I insert it, then I expect an empty emoji`() {
    installPack(packId1, packKey1)

    insertSticker(packId1, packKey1, stickerId = 1, emoji = null)

    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)!!.emoji).isEqualTo("")
  }

  @Test
  fun `given a sticker with no emoji, when I search by emoji, then I expect only the sticker that has one`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = null)
    insertSticker(packId1, packKey1, stickerId = 2, emoji = "\uD83D\uDC4D")

    val results = StickerTables.StickerRecordReader(SignalDatabase.stickers.getStickersByEmoji("\uD83D\uDC4D")).use { reader ->
      generateSequence { reader.getNext() }.map { it.stickerId }.toList()
    }

    assertThat(results).isEqualTo(listOf(2))
  }

  @Test
  fun `given stickers in multiple packs, when I get downloaded sticker ids, then I expect only non-cover stickers in the requested pack`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")
    insertSticker(packId1, packKey1, stickerId = 2, emoji = "")
    installPack(packId2, packKey2)
    insertSticker(packId2, packKey2, stickerId = 3, emoji = "")

    assertThat(SignalDatabase.stickers.getDownloadedStickerIds(packId1)).isEqualTo(setOf(1, 2))
  }

  @Test
  fun `given a sticker whose file is missing, when I get downloaded sticker ids, then I expect it excluded`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")
    insertSticker(packId1, packKey1, stickerId = 2, emoji = "")

    val filePath = SignalDatabase.stickers.readableDatabase
      .select(StickerTables.Sticker.FILE_PATH)
      .from(StickerTables.Sticker.TABLE_NAME)
      .where("${StickerTables.Sticker.PACK_ID} = ? AND ${StickerTables.Sticker.STICKER_ID} = ? AND ${StickerTables.Sticker.COVER} = 0", packId1, "2")
      .run()
      .readToSingleObject { it.requireNonNullString(StickerTables.Sticker.FILE_PATH) }!!
    File(filePath).delete()

    assertThat(SignalDatabase.stickers.getDownloadedStickerIds(packId1)).isEqualTo(setOf(1))
  }

  @Test
  fun `given a pack with no stickers, when I get downloaded sticker ids, then I expect none`() {
    installPack(packId1, packKey1)

    assertThat(SignalDatabase.stickers.getDownloadedStickerIds(packId1)).isEmpty()
  }

  @Test
  fun `given an installed pack, when I insert a pack reference for it, then I expect it listed once`() {
    installPack(packId1, packKey1)

    SignalDatabase.stickers.insertPackReference(packId1, packKey1)

    assertThat(installedPackIds()).isEqualTo(listOf(packId1))
  }

  @Test
  fun `given a pack reference, when I insert it again, then I expect the pack listed once`() {
    SignalDatabase.stickers.insertPackReference(packId1, packKey1)

    SignalDatabase.stickers.insertPackReference(packId1, packKey1)

    assertThat(installedPackIds()).isEqualTo(listOf(packId1))
  }

  @Test
  fun `given a sticker that is not stored, when I check if it is favorited, then I expect null`() {
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 1)).isNull()
  }

  @Test
  fun `given a sticker, when I favorite it, then I expect only favorited_at and a storage id to be set`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")

    SignalDatabase.stickers.setFavorite(packId1, 1, isFavorite = true)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 1)).isEqualTo(true)
    assertThat(favorite.favoritedAt).isNotEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isEqualTo(0L)
    assertThat(favorite.storageId).isNotNull()
  }

  @Test
  fun `given a favorited sticker, when I unfavorite it, then I expect a tombstone with a rotated storage id`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    val originalStorageId = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!.storageId

    SignalDatabase.stickers.setFavorite(packId1, 1, isFavorite = false)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 1)).isEqualTo(false)
    assertThat(favorite.favoritedAt).isEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isNotEqualTo(0L)
    assertThat(favorite.storageId).isNotNull()
    assertThat(favorite.storageId).isNotEqualTo(originalStorageId)
  }

  @Test
  fun `given favorited and unfavorited stickers, when I uninstall the pack, then I expect favorites and tombstones kept`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2)
    insertSticker(packId1, packKey1, stickerId = 3, emoji = "")

    SignalDatabase.stickers.uninstallPack(packId1)

    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)).isNotNull()
    assertThat(SignalDatabase.stickers.getSticker(packId1, 2, false)).isNotNull()
    assertThat(SignalDatabase.stickers.getSticker(packId1, 3, false)).isNull()
  }

  @Test
  fun `given an unfavorited sticker whose tombstone expired, when I uninstall the pack, then I expect it deleted`() {
    installPack(packId1, packKey1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 1)
    SignalDatabase.stickers.removeStorageIdsFromOldUnfavoritedStickers(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))

    SignalDatabase.stickers.uninstallPack(packId1)

    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)).isNull()
  }

  @Test
  fun `given an unfavorited sticker in an uninstalled pack, when I reinstall the pack, then I expect it to keep its tombstone`() {
    installPack(packId1, packKey1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 1)
    val original = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    SignalDatabase.stickers.uninstallPack(packId1)

    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(favorite.rowId).isEqualTo(original.rowId)
    assertThat(favorite.unfavoritedAt).isEqualTo(original.unfavoritedAt)
    assertThat(favorite.storageId).isEqualTo(original.storageId)
  }

  @Test
  fun `given a favorited sticker in an uninstalled pack, when I reinstall the pack, then I expect it to remain favorited`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    SignalDatabase.stickers.uninstallPack(packId1)

    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")

    assertThat(SignalDatabase.stickers.isFavorite(packId1, 1)).isEqualTo(true)
  }

  @Test
  fun `given orphaned packs, when I delete orphaned packs, then I expect packs with favorites to be kept`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    installPack(packId2, packKey2)
    insertUnfavoritedSticker(packId2, packKey2, stickerId = 1)
    installPack(packId3, packKey3)
    insertSticker(packId3, packKey3, stickerId = 1, emoji = "")
    SignalDatabase.stickers.uninstallPacks(setOf(StickerPackId(packId1), StickerPackId(packId2), StickerPackId(packId3)))
    SignalDatabase.stickers.removeStorageIdsFromOldDeletedPacks(Long.MAX_VALUE)
    SignalDatabase.stickers.removeStorageIdsFromOldUnfavoritedStickers(Long.MAX_VALUE)

    SignalDatabase.stickers.deleteOrphanedPacks()

    assertThat(SignalDatabase.stickers.getStickerPack(packId1)).isNotNull()
    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)).isNotNull()
    assertThat(SignalDatabase.stickers.getStickerPack(packId2)).isNull()
    assertThat(SignalDatabase.stickers.getStickerPack(packId3)).isNull()
  }

  @Test
  fun `given an unknown pack, when I insert a favorited sticker, then I expect it favorited in an uninstalled pack`() {
    insertDownloadedFavorite(packId1, packKey1, stickerId = 1)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(favorite.favoritedAt).isNotEqualTo(0L)
    assertThat(favorite.storageId).isNotNull()
    assertThat(SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!.installed).isFalse()
    assertThat(installedPackIds()).isEmpty()
  }

  @Test
  fun `given an unfavorited sticker, when I insert it as favorited, then I expect it favorited again`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")
    SignalDatabase.stickers.setFavorite(packId1, 1, isFavorite = false)

    insertDownloadedFavorite(packId1, packKey1, stickerId = 1)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(favorite.favoritedAt).isNotEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isEqualTo(0L)
    assertThat(SignalDatabase.stickers.isPackInstalled(packId1)).isTrue()
  }

  @Test
  fun `given a favorited sticker from an unknown pack, when I delete orphaned packs, then I expect it to be kept`() {
    insertDownloadedFavorite(packId1, packKey1, stickerId = 1)

    SignalDatabase.stickers.deleteOrphanedPacks()

    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)).isNotNull()
  }

  @Test
  fun `given favorited and unfavorited stickers, when I get the favorite count, then I expect only favorited stickers counted`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2)
    insertSticker(packId1, packKey1, stickerId = 3, emoji = "")

    assertThat(SignalDatabase.stickers.getFavoriteCount()).isEqualTo(1)
  }

  @Test
  fun `given favorited and unfavorited stickers, when I get favorite storage sync ids, then I expect both`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2)
    insertSticker(packId1, packKey1, stickerId = 3, emoji = "")

    val ids = SignalDatabase.stickers.getFavoriteStorageSyncIds()

    assertThat(ids).hasSize(2)
    assertThat(ids).contains(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!.storageId)
    assertThat(ids).contains(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 2)!!.storageId)
  }

  @Test
  fun `given favorited stickers, when I update their storage sync ids, then I expect an updated map`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 2)

    val existingMap = SignalDatabase.stickers.getFavoriteStorageSyncIdsMap()
    existingMap.forEach { (rowId, _) ->
      SignalDatabase.stickers.applyFavoriteStorageIdUpdate(rowId, StorageId.forFavoriteSticker(StorageSyncHelper.generateKey()))
    }
    val updatedMap = SignalDatabase.stickers.getFavoriteStorageSyncIdsMap()

    assertThat(updatedMap).hasSize(2)
    existingMap.forEach { (rowId, storageId) ->
      assertThat(updatedMap[rowId]).isNotEqualTo(storageId)
    }
  }

  @Test
  fun `given a remote favorite for an unknown pack, when I insert it locally, then I expect a placeholder favorite in an uninstalled pack`() {
    val remoteRecord = remoteFavorite(packId1, packKey1, stickerId = 4, favoritedAt = 1000L)

    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteRecord)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    assertThat(favorite.favoritedAt).isEqualTo(1000L)
    assertThat(favorite.unfavoritedAt).isEqualTo(0L)
    assertThat(favorite.packKey).isEqualTo(packKey1)
    assertThat(favorite.storageId).isEqualTo(remoteRecord.id)
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 4)).isEqualTo(true)
    assertThat(SignalDatabase.stickers.getPackForStorageSync(StickerPackId(packId1))!!.installed).isFalse()
    assertThat(favoriteStickerIds()).isEmpty()
  }

  @Test
  fun `given a deleted remote favorite for an unknown pack, when I insert it locally, then I expect a tombstone`() {
    val remoteRecord = remoteFavorite(packId1, packKey = null, stickerId = 4, deletedAt = 1000L)

    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteRecord)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    assertThat(favorite.favoritedAt).isEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isEqualTo(1000L)
    assertThat(favorite.storageId).isEqualTo(remoteRecord.id)
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 4)).isEqualTo(false)
  }

  @Test
  fun `given a placeholder from a deleted remote favorite, when I check for a file, then I expect none`() {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey = null, stickerId = 4, deletedAt = 1000L))

    assertThat(SignalDatabase.stickers.hasStickerFile(packId1, 4)).isFalse()
  }

  @Test
  fun `given a placeholder from a deleted remote favorite, when I insert it as favorited, then I expect it filled in with the pack key`() {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey = null, stickerId = 4, deletedAt = 1000L))
    val rowId = SignalDatabase.stickers.getSticker(packId1, 4, false)!!.rowId

    insertDownloadedFavorite(packId1, packKey1, stickerId = 4, emoji = "\uD83D\uDE00")

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    assertThat(favorite.rowId).isEqualTo(rowId)
    assertThat(favorite.favoritedAt).isNotEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isEqualTo(0L)
    assertThat(favorite.packKey).isEqualTo(packKey1)
    assertThat(SignalDatabase.stickers.hasStickerFile(packId1, 4)).isTrue()
    assertThat(favoriteStickerIds()).isEqualTo(listOf(4))
  }

  @Test
  fun `given favorites over the limit, when I unfavorite the newest, then I expect the newest unfavorited with new storage ids`() {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 1, favoritedAt = 1000L))
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 2, favoritedAt = 3000L))
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 3, favoritedAt = 2000L))
    val originalStorageId = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 2)!!.storageId

    val unfavorited = SignalDatabase.stickers.unfavoriteNewestOverLimit(limit = 2)

    val newest = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 2)!!
    assertThat(unfavorited).isEqualTo(1)
    assertThat(SignalDatabase.stickers.getFavoriteCount()).isEqualTo(2)
    assertThat(newest.favoritedAt).isEqualTo(0L)
    assertThat(newest.unfavoritedAt).isNotEqualTo(0L)
    assertThat(newest.storageId).isNotEqualTo(originalStorageId)
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 1)).isEqualTo(true)
    assertThat(SignalDatabase.stickers.isFavorite(packId1, 3)).isEqualTo(true)
  }

  @Test
  fun `given favorites at the limit, when I unfavorite the newest, then I expect nothing unfavorited`() {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 1, favoritedAt = 1000L))
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 2, favoritedAt = 2000L))

    val unfavorited = SignalDatabase.stickers.unfavoriteNewestOverLimit(limit = 2)

    assertThat(unfavorited).isEqualTo(0)
    assertThat(SignalDatabase.stickers.getFavoriteCount()).isEqualTo(2)
  }

  @Test
  fun `given a downloaded sticker, when I apply a remote favorite, then I expect it favorited in place`() {
    installPack(packId1, packKey1)
    insertSticker(packId1, packKey1, stickerId = 1, emoji = "")
    val rowId = SignalDatabase.stickers.getSticker(packId1, 1, false)!!.rowId

    val remoteRecord = remoteFavorite(packId1, packKey1, stickerId = 1, favoritedAt = 1000L)
    SignalDatabase.stickers.updateFavoriteFromStorageSync(remoteRecord)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(favorite.rowId).isEqualTo(rowId)
    assertThat(favorite.favoritedAt).isEqualTo(1000L)
    assertThat(favorite.storageId).isEqualTo(remoteRecord.id)
    assertThat(favoriteStickerIds()).isEqualTo(listOf(1))
  }

  @Test
  fun `given a favorited sticker, when I apply a deleted remote favorite, then I expect it unfavorited`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)

    val remoteRecord = remoteFavorite(packId1, packKey = null, stickerId = 1, deletedAt = 1000L)
    SignalDatabase.stickers.updateFavoriteFromStorageSync(remoteRecord)

    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!
    assertThat(favorite.favoritedAt).isEqualTo(0L)
    assertThat(favorite.unfavoritedAt).isEqualTo(1000L)
    assertThat(favorite.packKey).isEqualTo(packKey1)
    assertThat(favorite.storageId).isEqualTo(remoteRecord.id)
  }

  @Test
  fun `given an unfavorited sticker, when I clean up old tombstones, then I expect it to not have a storage id`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2)

    val removed = SignalDatabase.stickers.removeStorageIdsFromOldUnfavoritedStickers(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))

    assertThat(removed).isEqualTo(1)
    assertThat(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!.storageId).isNotNull()
    assertThat(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 2)!!.storageId).isNull()
  }

  @Test
  fun `given an unfavorited sticker in an uninstalled pack, when I clean up old tombstones, then I expect it deleted`() {
    installPack(packId1, packKey1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 1)
    SignalDatabase.stickers.uninstallPack(packId1)

    val removed = SignalDatabase.stickers.removeStorageIdsFromOldUnfavoritedStickers(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))

    assertThat(removed).isEqualTo(1)
    assertThat(SignalDatabase.stickers.getSticker(packId1, 1, false)).isNull()
  }

  @Test
  fun `given an unfavorited sticker missing from the remote manifest, when I remove local only tombstones, then I expect only it cleared`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1)
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2)

    val removed = SignalDatabase.stickers.removeStorageIdsFromLocalOnlyUnfavoritedStickers(SignalDatabase.stickers.getFavoriteStorageSyncIds())

    assertThat(removed).isEqualTo(1)
    assertThat(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 1)!!.storageId).isNotNull()
    assertThat(SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 2)!!.storageId).isNull()
  }

  @Test
  fun `given an unfavorited sticker in an uninstalled pack, when I search by emoji, then I expect it not returned`() {
    installPack(packId1, packKey1)
    insertFavoritedSticker(packId1, packKey1, stickerId = 1, emoji = "\uD83D\uDE00")
    insertUnfavoritedSticker(packId1, packKey1, stickerId = 2, emoji = "\uD83D\uDE00")
    SignalDatabase.stickers.uninstallPack(packId1)

    val results = StickerTables.StickerRecordReader(SignalDatabase.stickers.getStickersByEmoji("\uD83D\uDE00")).use { reader ->
      reader.asSequence().map { it.stickerId }.toList()
    }

    assertThat(results).isEqualTo(listOf(1))
  }

  @Test
  fun `given a remote favorite with unknown fields, when I apply it and read it back, then I expect the unknown fields preserved`() {
    val unknownFields = FavoriteStickerRecord(stickerId = 99).newBuilder()
      .addUnknownField(100, FieldEncoding.LENGTH_DELIMITED, "future".encodeUtf8())
      .build()
      .unknownFields
    val base = remoteFavorite(packId1, packKey1, stickerId = 4, favoritedAt = 1000L)
    val remoteRecord = base.copy(proto = base.proto.newBuilder().addUnknownFields(unknownFields).build())

    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteRecord)

    val local = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    val roundTripped = StorageSyncModels.localToRemoteFavoriteSticker(local, remoteRecord.id.raw)
    assertThat(local.storageServiceProto).isNotNull()
    assertThat(roundTripped.proto.unknownFields).isEqualTo(unknownFields)
    assertThat(roundTripped.proto.favoritedAtTimestamp).isEqualTo(1000L)
    assertThat(roundTripped.proto.stickerId).isEqualTo(4)
  }

  @Test
  fun `given a synced favorite placeholder, when its image is downloaded with manifest details, then I expect it filled in place`() {
    SignalDatabase.stickers.insertFavoriteFromStorageSync(remoteFavorite(packId1, packKey1, stickerId = 4, favoritedAt = 1000L))
    val placeholder = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    assertThat(favoriteStickerIds()).isEmpty()

    SignalDatabase.stickers.insertSticker(
      sticker = IncomingSticker(packId1, packKey1, "", "", 4, "\uD83D\uDE00", "image/apng", false, false),
      dataStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
      notify = false,
      updatePack = false
    )

    val sticker = SignalDatabase.stickers.getSticker(packId1, 4, false)!!
    val favorite = SignalDatabase.stickers.getFavoriteForStorageSync(StickerPackId(packId1), 4)!!
    assertThat(sticker.rowId).isEqualTo(placeholder.rowId)
    assertThat(sticker.emoji).isEqualTo("\uD83D\uDE00")
    assertThat(sticker.contentType).isEqualTo("image/apng")
    assertThat(favorite.favoritedAt).isEqualTo(1000L)
    assertThat(favorite.storageId).isEqualTo(placeholder.storageId)
    assertThat(favoriteStickerIds()).isEqualTo(listOf(4))
  }

  private val FavoriteStickerSyncRecord.storageId: StorageId?
    get() = storageServiceId?.let { StorageId.forFavoriteSticker(it) }

  private fun favoriteStickerIds(): List<Int> {
    return StickerTables.StickerRecordReader(SignalDatabase.stickers.getFavoriteStickers()).use { reader ->
      reader.asSequence().map { it.stickerId }.toList()
    }
  }

  private fun remoteFavorite(packId: String, packKey: String?, stickerId: Int, favoritedAt: Long = 0L, deletedAt: Long = 0L): SignalFavoriteStickerRecord {
    return SignalFavoriteStickerRecord(
      StorageId.forFavoriteSticker(StorageSyncHelper.generateKey()),
      FavoriteStickerRecord(
        packId = Hex.fromStringCondensed(packId).toByteString(),
        packKey = packKey?.let { Hex.fromStringCondensed(it).toByteString() } ?: ByteString.EMPTY,
        stickerId = stickerId,
        favoritedAtTimestamp = favoritedAt,
        deletedAtTimestamp = deletedAt
      )
    )
  }

  private fun installedPackIds(): List<String> {
    return StickerTables.StickerPackRecordReader(SignalDatabase.stickers.getInstalledStickerPacks()).use { reader ->
      reader.asSequence().map { it.packId }.toList()
    }
  }

  private fun installPack(packId: String, packKey: String) {
    insertSticker(packId, packKey, stickerId = 0, emoji = "", isCover = true)
  }

  private fun insertSticker(packId: String, packKey: String, stickerId: Int, emoji: String?, isCover: Boolean = false) {
    SignalDatabase.stickers.insertSticker(
      sticker = IncomingSticker(
        packId = packId,
        packKey = packKey,
        packTitle = "Title",
        packAuthor = "Author",
        stickerId = stickerId,
        emoji = emoji,
        contentType = "image/webp",
        isCover = isCover,
        isInstalled = true
      ),
      dataStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
      notify = false
    )
  }

  private fun insertFavoritedSticker(packId: String, packKey: String, stickerId: Int, emoji: String = "") {
    insertSticker(packId, packKey, stickerId, emoji)
    SignalDatabase.stickers.setFavorite(packId, stickerId, isFavorite = true)
  }

  private fun insertUnfavoritedSticker(packId: String, packKey: String, stickerId: Int, emoji: String = "") {
    insertFavoritedSticker(packId, packKey, stickerId, emoji)
    SignalDatabase.stickers.setFavorite(packId, stickerId, isFavorite = false)
  }

  private fun insertDownloadedFavorite(packId: String, packKey: String, stickerId: Int, emoji: String = "") {
    SignalDatabase.stickers.insertFavorite(packId, packKey, stickerId, emoji, contentType = "image/webp", dataStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
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

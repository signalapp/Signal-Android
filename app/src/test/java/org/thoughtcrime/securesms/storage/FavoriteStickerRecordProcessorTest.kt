package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalFavoriteStickerRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.FavoriteStickerRecord

/**
 * Tests for [FavoriteStickerRecordProcessor]
 */
class FavoriteStickerRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forFavoriteSticker(byteArrayOf(1, 2, 3, 4))

    val PACK_ID: ByteArray = ByteArray(16) { it.toByte() }
    val PACK_KEY: ByteArray = ByteArray(32) { it.toByte() }

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = FavoriteStickerRecordProcessor()

  @Test
  fun `Given a valid favorited proto, assert valid`() {
    // GIVEN
    val record = buildRecord(favoritedAt = 1000L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a valid deleted proto with no pack key, assert valid`() {
    // GIVEN
    val record = buildRecord(deletedAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with a bad pack id length, assert invalid`() {
    // GIVEN
    val record = buildRecord(packId = ByteArray(15), favoritedAt = 1000L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid favorited proto with a bad pack key length, assert invalid`() {
    // GIVEN
    val record = buildRecord(packKey = ByteArray(31), favoritedAt = 1000L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid favorited proto with no favorited timestamp, assert invalid`() {
    // GIVEN
    val record = buildRecord(favoritedAt = 0L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given two favorited records, when merged, assert remote wins`() {
    // GIVEN
    val remote = buildRecord(favoritedAt = 2000L)
    val local = buildRecord(favoritedAt = 1000L)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given a favorited remote record and a deleted local record, when merged, assert remote wins so the sticker is refavorited`() {
    // GIVEN
    val remote = buildRecord(favoritedAt = 2000L)
    val local = buildRecord(deletedAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given two deleted records, when merged, assert the earlier deletion wins`() {
    // GIVEN
    val remote = buildRecord(deletedAtTimestamp = 2000L)
    val local = buildRecord(deletedAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(local, result)
  }

  @Test
  fun `Given records with the same pack and sticker id, assert compare matches`() {
    // GIVEN
    val first = buildRecord(favoritedAt = 1000L)
    val second = buildRecord(favoritedAt = 2000L)
    val otherSticker = buildRecord(stickerId = 2, favoritedAt = 1000L)
    val otherPack = buildRecord(packId = ByteArray(16) { (it + 1).toByte() }, favoritedAt = 1000L)

    // THEN
    assertEquals(0, testSubject.compare(first, second))
    assertTrue(testSubject.compare(first, otherSticker) != 0)
    assertTrue(testSubject.compare(first, otherPack) != 0)
  }

  private fun buildRecord(packId: ByteArray = PACK_ID, packKey: ByteArray = PACK_KEY, stickerId: Int = 1, favoritedAt: Long = 0L, deletedAtTimestamp: Long = 0L): SignalFavoriteStickerRecord {
    val proto = FavoriteStickerRecord.Builder().apply {
      this.packId = packId.toByteString()
      this.stickerId = stickerId

      if (deletedAtTimestamp > 0) {
        this.deletedAtTimestamp = deletedAtTimestamp
      } else {
        this.packKey = packKey.toByteString()
        this.favoritedAtTimestamp = favoritedAt
      }
    }.build()

    return SignalFavoriteStickerRecord(STORAGE_ID, proto)
  }
}

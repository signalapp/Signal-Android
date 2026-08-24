package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord

/**
 * Tests for [StickerPackRecordProcessor]
 */
class StickerPackRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forStickerPack(byteArrayOf(1, 2, 3, 4))

    val PACK_ID: ByteArray = ByteArray(16) { it.toByte() }
    val PACK_KEY: ByteArray = ByteArray(32) { it.toByte() }

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = StickerPackRecordProcessor()

  @Test
  fun `Given a valid installed proto, assert valid`() {
    // GIVEN
    val proto = StickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
      packKey = PACK_KEY.toByteString()
      position = 1
    }.build()
    val record = SignalStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a valid deleted proto with no pack key, assert valid`() {
    // GIVEN
    val proto = StickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
      deletedAtTimestamp = 1000L
    }.build()
    val record = SignalStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with a bad pack id length, assert invalid`() {
    // GIVEN
    val proto = StickerPackRecord.Builder().apply {
      packId = ByteArray(15).toByteString()
      packKey = PACK_KEY.toByteString()
    }.build()
    val record = SignalStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid installed proto with a bad pack key length, assert invalid`() {
    // GIVEN
    val proto = StickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
      packKey = ByteArray(31).toByteString()
    }.build()
    val record = SignalStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid installed proto with no pack key, assert invalid`() {
    // GIVEN
    val proto = StickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
      position = 2
    }.build()
    val record = SignalStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given two installed records, when merged, assert remote wins`() {
    // GIVEN
    val remote = buildRecord(position = 5)
    val local = buildRecord(position = 3)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given a deleted remote record and an installed local record, when merged, assert remote wins`() {
    // GIVEN
    val remote = buildRecord(deletedAtTimestamp = 1000L)
    val local = buildRecord(position = 3)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given an installed remote record and a deleted local record, when merged, assert remote wins so the pack is reinstalled`() {
    // GIVEN
    val remote = buildRecord(position = 3)
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
  fun `Given records with the same pack id, assert compare matches`() {
    // GIVEN
    val first = buildRecord(position = 1)
    val second = buildRecord(position = 2)
    val other = buildRecord(packId = ByteArray(16) { (it + 1).toByte() }, position = 1)

    // THEN
    assertEquals(0, testSubject.compare(first, second))
    assertTrue(testSubject.compare(first, other) != 0)
  }

  private fun buildRecord(packId: ByteArray = PACK_ID, position: Int = 0, deletedAtTimestamp: Long = 0L): SignalStickerPackRecord {
    val proto = StickerPackRecord.Builder().apply {
      this.packId = packId.toByteString()

      if (deletedAtTimestamp > 0) {
        this.deletedAtTimestamp = deletedAtTimestamp
      } else {
        this.packKey = PACK_KEY.toByteString()
        this.position = position
      }
    }.build()

    return SignalStickerPackRecord(STORAGE_ID, proto)
  }
}

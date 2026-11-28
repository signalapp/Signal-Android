/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalHiddenStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.HiddenStickerPackRecord

/**
 * Tests for [HiddenStickerPackRecordProcessor]
 */
class HiddenStickerPackRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forHiddenStickerPack(byteArrayOf(1, 2, 3, 4))

    val PACK_ID: ByteArray = ByteArray(16) { it.toByte() }
    val PACK_KEY: ByteArray = ByteArray(32) { it.toByte() }

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = HiddenStickerPackRecordProcessor()

  @Test
  fun `Given a valid hidden proto, assert valid`() {
    // GIVEN
    val record = buildRecord()

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a valid unhidden proto with no pack key, assert valid`() {
    // GIVEN
    val record = buildRecord(unhiddenAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with a bad pack id length, assert invalid`() {
    // GIVEN
    val record = buildRecord(packId = ByteArray(15))

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid hidden proto with a bad pack key length, assert invalid`() {
    // GIVEN
    val proto = HiddenStickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
      packKey = ByteArray(31).toByteString()
    }.build()
    val record = SignalHiddenStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid hidden proto with no pack key, assert invalid`() {
    // GIVEN
    val proto = HiddenStickerPackRecord.Builder().apply {
      packId = PACK_ID.toByteString()
    }.build()
    val record = SignalHiddenStickerPackRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an unhidden remote record and a hidden local record, when merged, assert remote wins`() {
    // GIVEN
    val remote = buildRecord(unhiddenAtTimestamp = 1000L)
    val local = buildRecord()

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given a hidden remote record and an unhidden local record, when merged, assert remote wins so the pack is hidden again`() {
    // GIVEN
    val remote = buildRecord()
    val local = buildRecord(unhiddenAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(remote, result)
  }

  @Test
  fun `Given two unhidden records, when merged, assert the earlier unhide wins`() {
    // GIVEN
    val remote = buildRecord(unhiddenAtTimestamp = 2000L)
    val local = buildRecord(unhiddenAtTimestamp = 1000L)

    // WHEN
    val result = testSubject.merge(remote, local, StorageSyncHelper.KEY_GENERATOR)

    // THEN
    assertEquals(local, result)
  }

  @Test
  fun `Given records with the same pack id, assert compare matches`() {
    // GIVEN
    val first = buildRecord()
    val second = buildRecord(unhiddenAtTimestamp = 1000L)
    val other = buildRecord(packId = ByteArray(16) { (it + 1).toByte() })

    // THEN
    assertEquals(0, testSubject.compare(first, second))
    assertTrue(testSubject.compare(first, other) != 0)
  }

  private fun buildRecord(packId: ByteArray = PACK_ID, unhiddenAtTimestamp: Long = 0L): SignalHiddenStickerPackRecord {
    val proto = HiddenStickerPackRecord.Builder().apply {
      this.packId = packId.toByteString()

      if (unhiddenAtTimestamp > 0) {
        this.unhiddenAtTimestamp = unhiddenAtTimestamp
      } else {
        this.packKey = PACK_KEY.toByteString()
      }
    }.build()

    return SignalHiddenStickerPackRecord(STORAGE_ID, proto)
  }
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V288_CopyStickerDataHashStartToEndTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(287)

  // Constants copied from AttachmentTable to ensure test stability
  companion object {
    private const val TABLE_NAME = "attachment"
    private const val ID = "_id"
    private const val DATA_FILE = "data_file"
    private const val DATA_RANDOM = "data_random"
    private const val TRANSFER_STATE = "transfer_state"
    private const val STICKER_PACK_ID = "sticker_pack_id"
    private const val STICKER_PACK_KEY = "sticker_pack_key"
    private const val STICKER_ID = "sticker_id"
    private const val STICKER_EMOJI = "sticker_emoji"
    private const val DATA_HASH_START = "data_hash_start"
    private const val DATA_HASH_END = "data_hash_end"
    private const val TRANSFER_PROGRESS_DONE = 0
  }

  @Test
  fun migrate_whenStickerHasDataHashStartButNoDataHashEndAndTransferDone_copiesDataHashStartToEnd() {
    val stickerAttachmentId = insertStickerAttachment(
      stickerPackId = "test-pack-id",
      dataHashStart = "abc123def456",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(stickerAttachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo("abc123def456")
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo("abc123def456")
    }
  }

  @Test
  fun migrate_whenStickerAlreadyHasDataHashEnd_doesNotOverwrite() {
    val stickerAttachmentId = insertStickerAttachment(
      stickerPackId = "test-pack-id",
      dataHashStart = "abc123def456",
      dataHashEnd = "existing-hash-end",
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(stickerAttachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo("abc123def456")
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo("existing-hash-end")
    }
  }

  @Test
  fun migrate_whenStickerHasNoDataHashStart_doesNothing() {
    val stickerAttachmentId = insertStickerAttachment(
      stickerPackId = "test-pack-id",
      dataHashStart = null,
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(stickerAttachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo(true)
      assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo(true)
    }
  }

  @Test
  fun migrate_whenNonStickerAttachmentHasDataHashStart_doesNotCopy() {
    val regularAttachmentId = insertRegularAttachment(
      dataHashStart = "regular-hash-start",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(regularAttachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo("regular-hash-start")
      assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo(true)
    }
  }

  @Test
  fun migrate_whenMultipleStickerAttachmentsWithMixedStates_onlyCopiesWhenNeeded() {
    // Should copy (sticker with data_hash_start, no data_hash_end, transfer done)
    val copyId1 = insertStickerAttachment(
      stickerPackId = "pack-1",
      dataHashStart = "hash-start-1",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )
    val copyId2 = insertStickerAttachment(
      stickerPackId = "pack-2",
      dataHashStart = "hash-start-2",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    // Should NOT copy (already has data_hash_end)
    val noOverwriteId = insertStickerAttachment(
      stickerPackId = "pack-3",
      dataHashStart = "hash-start-3",
      dataHashEnd = "existing-end-3",
      transferState = TRANSFER_PROGRESS_DONE
    )

    // Should NOT copy (no data_hash_start)
    val noDataHashStartId = insertStickerAttachment(
      stickerPackId = "pack-4",
      dataHashStart = null,
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    // Should NOT copy (not a sticker)
    val nonStickerID = insertRegularAttachment(
      dataHashStart = "regular-hash",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    // Check that data_hash_start was copied to data_hash_end
    assertDataHashState(copyId1, expectedStart = "hash-start-1", expectedEnd = "hash-start-1")
    assertDataHashState(copyId2, expectedStart = "hash-start-2", expectedEnd = "hash-start-2")

    // Check that existing data_hash_end was not overwritten
    assertDataHashState(noOverwriteId, expectedStart = "hash-start-3", expectedEnd = "existing-end-3")

    // Check that null values remain null
    assertDataHashState(noDataHashStartId, expectedStart = null, expectedEnd = null)

    // Check that non-sticker attachment was not affected
    assertDataHashState(nonStickerID, expectedStart = "regular-hash", expectedEnd = null)
  }

  @Test
  fun migrate_whenNoStickersMatchCriteria_noChanges() {
    val noStickerPackId = insertRegularAttachment(
      dataHashStart = "hash1",
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )
    val stickerButNoHashStart = insertStickerAttachment(
      stickerPackId = "pack-1",
      dataHashStart = null,
      dataHashEnd = null,
      transferState = TRANSFER_PROGRESS_DONE
    )
    val stickerWithExistingEnd = insertStickerAttachment(
      stickerPackId = "pack-2",
      dataHashStart = "start-hash",
      dataHashEnd = "end-hash",
      transferState = TRANSFER_PROGRESS_DONE
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    // Check no changes were made
    assertDataHashState(noStickerPackId, expectedStart = "hash1", expectedEnd = null)
    assertDataHashState(stickerButNoHashStart, expectedStart = null, expectedEnd = null)
    assertDataHashState(stickerWithExistingEnd, expectedStart = "start-hash", expectedEnd = "end-hash")
  }

  @Test
  fun migrate_whenStickerTransferNotDone_doesNotCopy() {
    val stickerInProgressId = insertStickerAttachment(
      stickerPackId = "test-pack-id",
      dataHashStart = "abc123def456",
      dataHashEnd = null,
      transferState = 1 // TRANSFER_PROGRESS_STARTED
    )

    val db = signalDatabaseRule.database
    V288_CopyStickerDataHashStartToEnd.migrate(ApplicationProvider.getApplicationContext(), db, 287, 288)

    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(stickerInProgressId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)
      assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo("abc123def456")
      assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo(true)
    }
  }

  private fun insertStickerAttachment(stickerPackId: String, dataHashStart: String?, dataHashEnd: String?, transferState: Int = TRANSFER_PROGRESS_DONE): Long {
    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put(DATA_FILE, "/fake/path/sticker.webp")
      put(DATA_RANDOM, "/fake/path/sticker.webp".toByteArray())
      put(TRANSFER_STATE, transferState)
      put(STICKER_PACK_ID, stickerPackId)
      put(STICKER_PACK_KEY, "test-pack-key")
      put(STICKER_ID, 1)
      put(STICKER_EMOJI, "😀")
      put(DATA_HASH_START, dataHashStart)
      put(DATA_HASH_END, dataHashEnd)
    }

    return db.insert(TABLE_NAME, null, values)
  }

  private fun insertRegularAttachment(dataHashStart: String?, dataHashEnd: String?, transferState: Int = TRANSFER_PROGRESS_DONE): Long {
    val db = signalDatabaseRule.database

    val values = ContentValues().apply {
      put(DATA_FILE, "/fake/path/regular.jpg")
      put(DATA_RANDOM, "/fake/path/regular.jpg".toByteArray())
      put(TRANSFER_STATE, transferState)
      put(DATA_HASH_START, dataHashStart)
      put(DATA_HASH_END, dataHashEnd)
      // No sticker fields - this makes it a regular attachment
    }

    return db.insert(TABLE_NAME, null, values)
  }

  private fun assertDataHashState(attachmentId: Long, expectedStart: String?, expectedEnd: String?) {
    val db = signalDatabaseRule.database
    val cursor = db.query(
      TABLE_NAME,
      arrayOf(DATA_HASH_START, DATA_HASH_END),
      "$ID = ?",
      arrayOf(attachmentId.toString()),
      null,
      null,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isEqualTo(true)

      if (expectedStart == null) {
        assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo(true)
      } else {
        assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_START))).isEqualTo(expectedStart)
      }

      if (expectedEnd == null) {
        assertThat(it.isNull(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo(true)
      } else {
        assertThat(it.getString(it.getColumnIndexOrThrow(DATA_HASH_END))).isEqualTo(expectedEnd)
      }
    }
  }
}

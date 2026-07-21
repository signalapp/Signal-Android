/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.readToSingleInt
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V322_NormalizeStickerTableTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(321)

  private val db get() = signalDatabaseRule.database

  companion object {
    private const val STICKER_TABLE = "sticker"
    private const val PACK_TABLE = "sticker_pack"
  }

  @Test
  fun migrate_splitsPackDetailsIntoStickerPackTable() {
    // Pack A: a cover plus two stickers, installed
    val coverAId = insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 0, cover = 1, packOrder = 5, installed = 1, emoji = "🅰️")
    insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 1, cover = 0, packOrder = 5, installed = 1, emoji = "😀")
    insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 2, cover = 0, packOrder = 5, installed = 1, emoji = "😁")

    // Pack B: cover only, not installed
    insertLegacySticker(packId = "packB", packKey = "keyB", title = "Title B", author = "Author B", stickerId = 0, cover = 1, packOrder = 2, installed = 0, emoji = "🅱️")

    V322_NormalizeStickerTable.migrate(ApplicationProvider.getApplicationContext(), db, 321, 322)

    assertThat(SqlUtil.tableExists(db, PACK_TABLE)).isTrue()

    // One pack row per distinct pack
    assertThat(rowCount(PACK_TABLE)).isEqualTo(2)

    assertPack(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", packOrder = 5, installed = true)
    assertPack(packId = "packB", packKey = "keyB", title = "Title B", author = "Author B", packOrder = 2, installed = false)

    // All sticker rows preserved
    assertThat(rowCount(STICKER_TABLE)).isEqualTo(4)

    // The cover's _id is preserved so existing sticker URIs remain valid
    val coverA = db
      .select("_id", "pack_id", "sticker_id", "cover", "emoji", "file_path")
      .from(STICKER_TABLE)
      .where("_id = ?", coverAId)
      .run()

    coverA.use {
      assertThat(it.moveToFirst()).isTrue()
      assertThat(it.requireLong("_id")).isEqualTo(coverAId)
      assertThat(it.requireNonNullString("pack_id")).isEqualTo("packA")
      assertThat(it.requireInt("cover")).isEqualTo(1)
      assertThat(it.requireNonNullString("emoji")).isEqualTo("🅰️")
    }
  }

  @Test
  fun migrate_removesPackColumnsFromStickerTable() {
    insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 0, cover = 1, packOrder = 0, installed = 1, emoji = "🅰️")

    V322_NormalizeStickerTable.migrate(ApplicationProvider.getApplicationContext(), db, 321, 322)

    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "pack_key")).isFalse()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "pack_title")).isFalse()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "pack_author")).isFalse()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "pack_order")).isFalse()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "installed")).isFalse()

    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "pack_id")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "sticker_id")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "cover")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "emoji")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "content_type")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "last_used")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "file_path")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "file_length")).isTrue()
    assertThat(SqlUtil.columnExists(db, STICKER_TABLE, "file_random")).isTrue()
  }

  @Test
  fun migrate_addsForeignKeyFromStickerToStickerPack() {
    insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 0, cover = 1, packOrder = 0, installed = 1, emoji = "🅰️")

    V322_NormalizeStickerTable.migrate(ApplicationProvider.getApplicationContext(), db, 321, 322)

    val cursor = db.rawQuery("PRAGMA foreign_key_list($STICKER_TABLE)", null)

    cursor.use {
      assertThat(it.moveToFirst()).isTrue()
      assertThat(it.requireNonNullString("table")).isEqualTo(PACK_TABLE)
      assertThat(it.requireNonNullString("from")).isEqualTo("pack_id")
      assertThat(it.requireNonNullString("to")).isEqualTo("pack_id")
      assertThat(it.requireNonNullString("on_delete")).isEqualTo("CASCADE")
    }
  }

  @Test
  fun migrate_whenPackHasNoCoverRow_stillCreatesPackRow() {
    // A pack that somehow only has non-cover stickers
    insertLegacySticker(packId = "orphanPack", packKey = "orphanKey", title = "Orphan", author = "Nobody", stickerId = 1, cover = 0, packOrder = 3, installed = 1, emoji = "🧩")

    V322_NormalizeStickerTable.migrate(ApplicationProvider.getApplicationContext(), db, 321, 322)

    assertThat(rowCount(PACK_TABLE)).isEqualTo(1)
    assertPack(packId = "orphanPack", packKey = "orphanKey", title = "Orphan", author = "Nobody", packOrder = 3, installed = true)
  }

  @Test
  fun migrate_joinReconstructsDenormalizedRow() {
    insertLegacySticker(packId = "packA", packKey = "keyA", title = "Title A", author = "Author A", stickerId = 1, cover = 0, packOrder = 5, installed = 1, emoji = "😀")

    V322_NormalizeStickerTable.migrate(ApplicationProvider.getApplicationContext(), db, 321, 322)

    val cursor = db.rawQuery(
      """
      SELECT sticker_pack.pack_key AS pack_key, sticker_pack.pack_title AS pack_title, sticker_pack.installed AS installed, sticker.emoji AS emoji
      FROM sticker INNER JOIN sticker_pack ON sticker.pack_id = sticker_pack.pack_id
      WHERE sticker.sticker_id = 1
      """,
      null
    )

    cursor.use {
      assertThat(it.moveToFirst()).isTrue()
      assertThat(it.requireNonNullString("pack_key")).isEqualTo("keyA")
      assertThat(it.requireNonNullString("pack_title")).isEqualTo("Title A")
      assertThat(it.requireBoolean("installed")).isTrue()
      assertThat(it.requireNonNullString("emoji")).isEqualTo("😀")
    }
  }

  private fun insertLegacySticker(
    packId: String,
    packKey: String,
    title: String,
    author: String,
    stickerId: Int,
    cover: Int,
    packOrder: Int,
    installed: Int,
    emoji: String
  ): Long {
    return db
      .insertInto(STICKER_TABLE)
      .values(
        "pack_id" to packId,
        "pack_key" to packKey,
        "pack_title" to title,
        "pack_author" to author,
        "sticker_id" to stickerId,
        "cover" to cover,
        "pack_order" to packOrder,
        "emoji" to emoji,
        "content_type" to "image/webp",
        "last_used" to 0,
        "installed" to installed,
        "file_path" to "/fake/path/$packId-$stickerId.webp",
        "file_length" to 1024L,
        "file_random" to "random".toByteArray()
      )
      .run()
  }

  private fun rowCount(table: String): Int {
    return db.rawQuery("SELECT COUNT(*) FROM $table", null).readToSingleInt()
  }

  private fun assertPack(packId: String, packKey: String, title: String, author: String, packOrder: Int, installed: Boolean) {
    val cursor = db
      .select("pack_key", "pack_title", "pack_author", "pack_order", "installed")
      .from(PACK_TABLE)
      .where("pack_id = ?", packId)
      .run()

    cursor.use {
      assertThat(it.moveToFirst()).isTrue()
      assertThat(it.requireNonNullString("pack_key")).isEqualTo(packKey)
      assertThat(it.requireNonNullString("pack_title")).isEqualTo(title)
      assertThat(it.requireNonNullString("pack_author")).isEqualTo(author)
      assertThat(it.requireInt("pack_order")).isEqualTo(packOrder)
      assertThat(it.requireBoolean("installed")).isEqualTo(installed)
    }
  }
}

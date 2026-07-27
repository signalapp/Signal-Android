/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireInt
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V323_AddStickerPackStorageSyncTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(322)

  private val db get() = signalDatabaseRule.database

  @Test
  fun migrate_remapsPackOrderToPositionsForInstalledPacks() {
    insertPack(packId = "packA", packOrder = 0, installed = 1)
    insertPack(packId = "packB", packOrder = 1, installed = 1)
    insertPack(packId = "packC", packOrder = 2, installed = 1)
    insertPack(packId = "packD", packOrder = 0, installed = 0)

    V323_AddStickerPackStorageSync.migrate(ApplicationProvider.getApplicationContext(), db, 322, 323)

    // Packs render in ascending position order: the first displayed pack gets position 0
    assertThat(positionOf("packA")).isEqualTo(0)
    assertThat(positionOf("packB")).isEqualTo(1)
    assertThat(positionOf("packC")).isEqualTo(2)

    // Uninstalled packs keep the default position and no storage id
    assertThat(positionOf("packD")).isEqualTo(0)
    assertThat(storageIdOf("packD")).isNull()
  }

  @Test
  fun migrate_remapsNullPackOrderToLowestPosition() {
    // A never-reordered pack has a NULL pack_order and displays first, so it gets the lowest position
    insertPack(packId = "packA", packOrder = null, installed = 1)
    insertPack(packId = "packB", packOrder = 0, installed = 1)
    insertPack(packId = "packC", packOrder = 1, installed = 1)

    V323_AddStickerPackStorageSync.migrate(ApplicationProvider.getApplicationContext(), db, 322, 323)

    assertThat(positionOf("packA")).isEqualTo(0)
    assertThat(positionOf("packB")).isEqualTo(1)
    assertThat(positionOf("packC")).isEqualTo(2)
  }

  @Test
  fun migrate_dropsPackOrderColumn() {
    insertPack(packId = "packA", packOrder = 0, installed = 1)

    V323_AddStickerPackStorageSync.migrate(ApplicationProvider.getApplicationContext(), db, 322, 323)

    assertThat(SqlUtil.columnExists(db, "sticker_pack", "pack_order")).isFalse()
    assertThat(SqlUtil.columnExists(db, "sticker_pack", "position")).isTrue()
    assertThat(SqlUtil.columnExists(db, "sticker_pack", "storage_service_id")).isTrue()
    assertThat(SqlUtil.columnExists(db, "sticker_pack", "storage_service_proto")).isTrue()
    assertThat(SqlUtil.columnExists(db, "sticker_pack", "deleted_timestamp_ms")).isTrue()
  }

  @Test
  fun migrate_addsColumnsWithDefaults() {
    insertPack(packId = "packA", packOrder = 0, installed = 1)

    V323_AddStickerPackStorageSync.migrate(ApplicationProvider.getApplicationContext(), db, 322, 323)

    val deletedTimestamp = db
      .select("deleted_timestamp_ms")
      .from("sticker_pack")
      .where("pack_id = ?", "packA")
      .run()
      .readToSingleObject { it.requireInt("deleted_timestamp_ms") }

    assertThat(deletedTimestamp).isEqualTo(0)
    assertThat(storageIdOf("packA")).isNull()
  }

  private fun insertPack(packId: String, packOrder: Int?, installed: Int) {
    db.insertInto("sticker_pack")
      .values(
        "pack_id" to packId,
        "pack_key" to "key-$packId",
        "pack_title" to "Title",
        "pack_author" to "Author",
        "pack_order" to packOrder,
        "installed" to installed
      )
      .run()
  }

  private fun positionOf(packId: String): Int? {
    return db
      .select("position")
      .from("sticker_pack")
      .where("pack_id = ?", packId)
      .run()
      .readToSingleObject { it.requireInt("position") }
  }

  private fun storageIdOf(packId: String): String? {
    return db
      .select("storage_service_id")
      .from("sticker_pack")
      .where("pack_id = ?", packId)
      .run()
      .readToSingleObject { it.requireString("storage_service_id") }
  }
}

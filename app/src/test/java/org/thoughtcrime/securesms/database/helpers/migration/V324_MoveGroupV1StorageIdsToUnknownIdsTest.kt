/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.insertInto
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V324_MoveGroupV1StorageIdsToUnknownIdsTest {

  companion object {
    private const val RECIPIENT_TYPE_GV1 = 2
    private const val RECIPIENT_TYPE_GV2 = 3
    private const val MANIFEST_TYPE_GROUPV1 = 2
  }

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(323)

  private val db get() = signalDatabaseRule.database

  @Test
  fun migrate_movesGroupV1StorageIdIntoUnknownIds() {
    insertRecipient(groupId = "gv1", type = RECIPIENT_TYPE_GV1, storageId = "storage-id-gv1")

    migrate()

    assertThat(unknownIdKeys()).isEqualTo(listOf("storage-id-gv1"))
    assertThat(unknownIdTypeOf("storage-id-gv1")).isEqualTo(MANIFEST_TYPE_GROUPV1)
  }

  @Test
  fun migrate_clearsGroupV1StorageIdAndProto() {
    insertRecipient(groupId = "gv1", type = RECIPIENT_TYPE_GV1, storageId = "storage-id-gv1", storageProto = "proto")

    migrate()

    assertThat(storageIdOf("gv1")).isNull()
    assertThat(storageProtoOf("gv1")).isNull()
  }

  @Test
  fun migrate_leavesOtherRecipientTypesAlone() {
    insertRecipient(groupId = "gv2", type = RECIPIENT_TYPE_GV2, storageId = "storage-id-gv2")

    migrate()

    assertThat(storageIdOf("gv2")).isEqualTo("storage-id-gv2")
    assertThat(unknownIdKeys()).isEqualTo(emptyList())
  }

  @Test
  fun migrate_ignoresGroupV1RecipientsWithoutAStorageId() {
    insertRecipient(groupId = "gv1", type = RECIPIENT_TYPE_GV1, storageId = null)

    migrate()

    assertThat(unknownIdKeys()).isEqualTo(emptyList())
  }

  /** The unknown ID table has a UNIQUE constraint on key, so a pre-existing row must not blow up the insert. */
  @Test
  fun migrate_toleratesAnIdThatIsAlreadyTracked() {
    insertRecipient(groupId = "gv1", type = RECIPIENT_TYPE_GV1, storageId = "storage-id-gv1")
    db.insertInto("storage_key").values("type" to MANIFEST_TYPE_GROUPV1, "key" to "storage-id-gv1").run()

    migrate()

    assertThat(unknownIdKeys()).isEqualTo(listOf("storage-id-gv1"))
    assertThat(storageIdOf("gv1")).isNull()
  }

  private fun migrate() {
    V324_MoveGroupV1StorageIdsToUnknownIds.migrate(ApplicationProvider.getApplicationContext(), db, 323, 324)
  }

  private fun insertRecipient(groupId: String, type: Int, storageId: String?, storageProto: String? = null) {
    db.insertInto("recipient")
      .values(
        "group_id" to groupId,
        "type" to type,
        "storage_service_id" to storageId,
        "storage_service_proto" to storageProto
      )
      .run()
  }

  private fun unknownIdKeys(): List<String> {
    return db.select("key").from("storage_key").run().readToList { it.requireNonNullString("key") }
  }

  private fun unknownIdTypeOf(key: String): Int? {
    return db.select("type").from("storage_key").where("key = ?", key).run().readToSingleObject { it.requireInt("type") }
  }

  private fun storageIdOf(groupId: String): String? {
    return db.select("storage_service_id").from("recipient").where("group_id = ?", groupId).run().readToSingleObject { it.requireString("storage_service_id") }
  }

  private fun storageProtoOf(groupId: String): String? {
    return db.select("storage_service_proto").from("recipient").where("group_id = ?", groupId).run().readToSingleObject { it.requireString("storage_service_proto") }
  }
}

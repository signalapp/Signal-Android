package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Migrates all IDs from the GroupTable into the GroupMembershipTable
 */
@Suppress("ClassName")
object V172_GroupMembershipMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE group_membership (
        _id INTEGER PRIMARY KEY,
        group_id TEXT NOT NULL,
        recipient_id INTEGER NOT NULL,
        UNIQUE(group_id, recipient_id)
      );
      """.trimIndent()
    )

    //language=sql
    val total = db.query("SELECT COUNT(*) FROM groups").use {
      if (it.moveToFirst()) {
        it.getInt(0)
      } else {
        0
      }
    }

    (0..total).chunked(500).forEachIndexed { index, _ ->
      //language=sql
      val groupIdToMembers: List<Pair<String, List<Long>>> = db.query("SELECT members, group_id FROM groups LIMIT 500 OFFSET ${index * 500}").readToList { cursor ->
        val groupId = cursor.requireNonNullString("group_id")
        val members: List<Long> = cursor.requireNonNullString("members").split(",").filterNot { it.isEmpty() }.map { it.toLong() }

        groupId to members
      }

      for ((group_id, members) in groupIdToMembers) {
        val queries = SqlUtil.buildBulkInsert(
          "group_membership",
          arrayOf("group_id", "recipient_id"),
          members.map {
            contentValuesOf(
              "group_id" to group_id,
              "recipient_id" to it
            )
          }
        )

        for (query in queries) {
          db.execSQL("${query.where} ON CONFLICT (group_id, recipient_id) DO NOTHING", query.whereArgs)
        }
      }
    }

    db.execSQL("ALTER TABLE groups DROP COLUMN members")
  }
}

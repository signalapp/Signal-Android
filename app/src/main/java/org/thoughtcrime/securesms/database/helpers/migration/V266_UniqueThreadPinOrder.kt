/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Somehow we have a bug where pinned orders aren't always unique. Could be from some old bug, not clear.
 * Regardless, we'll add some guarantees in the schema itself.
 *
 *  We want to add a unique constraint on pinned order. To do that, we need to move to using NULL instead of 0
 * for the unset state. That includes changing the default value, which requires a table copy.
 *
 * While we're at it, we'll also change the column to pinned_order, since it's an order and not a boolean.
 */
@Suppress("ClassName")
object V266_UniqueThreadPinOrder : SignalDatabaseMigration {
  private val TAG = Log.tag(V266_UniqueThreadPinOrder::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    // 1. Convert all 0's to nulls
    db.execSQL(
      """
        UPDATE thread
        SET pinned = null
        WHERE pinned = 0
      """
    )
    stopwatch.split("nulls")

    // 2. Correct any duplicate pinned columns
    val pinnedThreads = db.rawQuery(
      """
        SELECT _id, pinned, last_seen
        FROM thread
        WHERE pinned NOT NULL
        ORDER BY pinned DESC
      """
    ).readToList { cursor ->
      ThreadPinnedData(
        id = cursor.requireLong("_id"),
        pinned = cursor.requireInt("pinned"),
        lastSeen = cursor.requireLong("last_seen")
      )
    }

    if (pinnedThreads.isNotEmpty()) {
      if (pinnedThreads.distinctBy { it.pinned }.size != pinnedThreads.size) {
        Log.w(TAG, "There's a duplicate pinned value! Correcting.")
        pinnedThreads
          .sortedBy { it.lastSeen }
          .sortedBy { it.pinned }
          .mapIndexed { i, thread -> thread.copy(pinned = i + 1) }
          .forEach { thread ->
            val values = contentValuesOf("pinned" to thread.pinned)
            db.update("thread", values, "_id = ${thread.id}", null)
          }
      }
    }
    stopwatch.split("fix-dupes")

    // 3. Create the new schema and copy everything over
    db.execSQL(
      """
        CREATE TABLE thread_tmp (
          _id INTEGER PRIMARY KEY AUTOINCREMENT, 
          date INTEGER DEFAULT 0, 
          meaningful_messages INTEGER DEFAULT 0,
          recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE,
          read INTEGER DEFAULT 1, 
          type INTEGER DEFAULT 0, 
          error INTEGER DEFAULT 0, 
          snippet TEXT, 
          snippet_type INTEGER DEFAULT 0, 
          snippet_uri TEXT DEFAULT NULL, 
          snippet_content_type TEXT DEFAULT NULL, 
          snippet_extras TEXT DEFAULT NULL, 
          unread_count INTEGER DEFAULT 0, 
          archived INTEGER DEFAULT 0, 
          status INTEGER DEFAULT 0, 
          has_delivery_receipt INTEGER DEFAULT 0, 
          has_read_receipt INTEGER DEFAULT 0, 
          expires_in INTEGER DEFAULT 0, 
          last_seen INTEGER DEFAULT 0, 
          has_sent INTEGER DEFAULT 0, 
          last_scrolled INTEGER DEFAULT 0, 
          pinned_order INTEGER UNIQUE DEFAULT NULL, 
          unread_self_mention_count INTEGER DEFAULT 0,
          active INTEGER DEFAULT 0,
          snippet_message_extras BLOB DEFAULT NULL
        )
      """
    )
    stopwatch.split("table-create")

    db.execSQL(
      """
        INSERT INTO thread_tmp 
          SELECT 
            _id, 
            date, 
            meaningful_messages,
            recipient_id,
            read, 
            type, 
            error, 
            snippet, 
            snippet_type, 
            snippet_uri, 
            snippet_content_type, 
            snippet_extras, 
            unread_count, 
            archived, 
            status, 
            has_delivery_receipt, 
            has_read_receipt, 
            expires_in, 
            last_seen, 
            has_sent, 
            last_scrolled, 
            pinned, 
            unread_self_mention_count,
            active,
            snippet_message_extras
          FROM thread
      """
    )
    stopwatch.split("table-copy")

    db.execSQL("DROP TABLE thread")
    db.execSQL("ALTER TABLE thread_tmp RENAME TO thread")
    stopwatch.split("replace")

    db.execSQL("CREATE INDEX IF NOT EXISTS thread_recipient_id_index ON thread (recipient_id, active);")
    db.execSQL("CREATE INDEX IF NOT EXISTS archived_count_index ON thread (active, archived, meaningful_messages, pinned_order);")
    db.execSQL("CREATE INDEX IF NOT EXISTS thread_pinned_index ON thread (pinned_order);")
    db.execSQL("CREATE INDEX IF NOT EXISTS thread_read ON thread (read);")
    db.execSQL("CREATE INDEX IF NOT EXISTS thread_active ON thread (active);")
    stopwatch.split("indexes")

    stopwatch.stop(TAG)
  }

  data class ThreadPinnedData(
    val id: Long,
    val pinned: Int,
    val lastSeen: Long
  )
}

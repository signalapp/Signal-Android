package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log

/**
 * We saw evidence (via failed backup restores) that some people have recipients in their thread table that do not exist in the recipient table.
 * This is likely the result of a bad past migration, since a foreign key is in place. Cleaning it up now.
 */
object V181_ThreadTableForeignKeyCleanup : SignalDatabaseMigration {

  val TAG = Log.tag(V181_ThreadTableForeignKeyCleanup::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("deletes")

    try {
      val threadDeleteCount = db.delete("thread", "recipient_id NOT IN (SELECT _id FROM recipient)", null)
      Log.i(TAG, "Deleted $threadDeleteCount threads.")
      stopwatch.split("threads")

      if (threadDeleteCount == 0) {
        Log.w(TAG, "No threads deleted. Finishing early.")
        return
      }

      val messageDeleteCount = db.delete("message", "thread_id NOT IN (SELECT _id FROM thread)", null)
      Log.i(TAG, "Deleted $messageDeleteCount messages.")
      stopwatch.split("messages")

      if (messageDeleteCount == 0) {
        Log.w(TAG, "No messages deleted. Finishing early.")
        return
      }

      val storySendDeleteCount = db.delete("story_sends", "message_id NOT IN (SELECT _id FROM message)", null)
      Log.i(TAG, "Deleted $storySendDeleteCount story_sends.")
      stopwatch.split("story_sends")

      val reactionDeleteCount = db.delete("reaction", "message_id NOT IN (SELECT _id FROM message)", null)
      Log.i(TAG, "Deleted $reactionDeleteCount reactions.")
      stopwatch.split("reactions")

      val callDeleteCount = db.delete("call", "message_id NOT IN (SELECT _id FROM message)", null)
      Log.i(TAG, "Deleted $callDeleteCount calls.")
      stopwatch.split("calls")
    } finally {
      stopwatch.stop(TAG)
    }
  }
}

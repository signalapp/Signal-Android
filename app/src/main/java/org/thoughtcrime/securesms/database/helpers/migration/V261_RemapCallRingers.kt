package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Previously, we weren't properly remapping call ringers when recipients were remapped. This repairs those scenarios the best we can.
 */
@Suppress("ClassName")
object V261_RemapCallRingers : SignalDatabaseMigration {

  private val TAG = Log.tag(V261_RemapCallRingers::class)
  private const val TEMP_INDEX = "tmp_call_ringer"

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // The following queries are really expensive without an index. So we create a temporary one.
    db.execSQL("CREATE INDEX $TEMP_INDEX ON call (ringer)")
    try {
      doMigration(db)
    } finally {
      db.execSQL("DROP INDEX IF EXISTS $TEMP_INDEX")
    }
  }

  private fun doMigration(db: SQLiteDatabase) {
    // Even with an index, the updates can be a little expensive, so we try to figure out if we need them at all by using a quick check.
    val invalidRingerCount = db
      .select("count(*)")
      .from("call INDEXED BY $TEMP_INDEX")
      .where("ringer != 0 AND ringer NOT IN (SELECT _id FROM recipient)")
      .run()
      .readToSingleLong()

    if (invalidRingerCount == 0L) {
      Log.i(TAG, "No invalid call ringers, can skip migration.")
      return
    }

    // Remap all call ringers using a remapped recipient
    db.execSQL(
      """
        UPDATE 
          call INDEXED BY $TEMP_INDEX
        SET 
          ringer = (SELECT new_id FROM remapped_recipients WHERE old_id = call.ringer)
        WHERE 
          ringer IN (SELECT old_id FROM remapped_recipients)
      """
    )

    // If there are any remaining call ringers that don't reference a real recipient, we have no choice but to delete the call
    db.execSQL(
      """
        DELETE FROM call INDEXED BY $TEMP_INDEX
        WHERE ringer != 0 AND ringer NOT IN (SELECT _id FROM recipient)
      """
    )
  }
}

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adjust notification profile schedules with end times between midnight at 1am. These were originally
 * stored as 24xx and will now use the same as start with 00xx.
 */
@Suppress("ClassName")
object V259_AdjustNotificationProfileMidnightEndTimes : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
        UPDATE
          notification_profile_schedule
        SET 'end' = end - 2400
        WHERE
        end >= 2400
      """
    )
  }
}

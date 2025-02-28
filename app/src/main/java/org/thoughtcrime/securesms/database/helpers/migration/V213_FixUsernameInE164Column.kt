/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * There was a bug where adding a member to a group by username could put that username in the e164 column.
 * We have to clean it up and move that value to the username column.
 */
object V213_FixUsernameInE164Column : SignalDatabaseMigration {

  private val TAG = Log.tag(V213_FixUsernameInE164Column::class.java)

  /** We rely on foreign keys to clean up data from bad recipients */
  override val enableForeignKeys: Boolean = true

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // In order to avoid unique constraint violations, we run this query once to move over everything that doesn't break any violations...
    db.execSQL(
      """
      UPDATE 
        recipient
      SET 
        username = e164,
        e164 = NULL
      WHERE 
        e164 GLOB '[a-zA-Z][a-zA-Z0-9][a-zA-Z0-9]*.[0-9][0-9]*'
          AND e164 NOT IN (SELECT username FROM recipient WHERE username IS NOT NULL)
      """
    )

    // ...and again to just clear out any remaining bad data. This should only clear data that would otherwise violate the unique constraint.
    db.execSQL(
      """
      UPDATE 
        recipient
      SET 
        e164 = NULL
      WHERE 
        e164 GLOB '[a-zA-Z][a-zA-Z0-9][a-zA-Z0-9]*.[0-9][0-9]*'
      """
    )

    // Finally, the above queries may have created recipients that have a username but no ACI. These are invalid entries that need to be trimmed.
    // Given that usernames are not public, we'll rely on cascading deletes here to clean things up (foreign keys are enabled for this migration).
    db.delete("recipient", "username IS NOT NULL AND aci IS NULL", null).let { deleteCount ->
      Log.i(TAG, "Deleted $deleteCount username-only recipients.")
    }
  }
}

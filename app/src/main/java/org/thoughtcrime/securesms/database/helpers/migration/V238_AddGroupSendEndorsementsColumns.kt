/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Add columns to group and group membership tables needed for group send endorsements.
 */
@Suppress("ClassName")
object V238_AddGroupSendEndorsementsColumns : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE groups ADD COLUMN group_send_endorsements_expiration INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE group_membership ADD COLUMN endorsement BLOB DEFAULT NULL")
  }
}

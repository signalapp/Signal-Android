/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the quote_target_content_type column to attachments and migrates existing quote attachments
 * to populate this field with their current content_type.
 */
@Suppress("ClassName")
object V289_AddQuoteTargetContentTypeColumn : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE attachment ADD COLUMN quote_target_content_type TEXT DEFAULT NULL;")
    db.execSQL("UPDATE attachment SET quote_target_content_type = content_type WHERE quote != 0;")
  }
}

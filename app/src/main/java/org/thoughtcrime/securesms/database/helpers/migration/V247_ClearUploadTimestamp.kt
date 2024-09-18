/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * There was a bad interaction with the digest backfill job, where digests could be changed, and then already-uploaded attachments could be re-used
 * but with a no-longer-matching digest. This migration set the upload timestamp to 1 for all uploaded attachments so that we don't re-use them.
 */
@Suppress("ClassName")
object V247_ClearUploadTimestamp : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("UPDATE attachment SET upload_timestamp = 1 WHERE upload_timestamp > 0")
  }
}

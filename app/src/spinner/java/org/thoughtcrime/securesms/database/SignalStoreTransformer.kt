/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.Cursor
import com.squareup.wire.ProtoAdapter
import org.signal.core.util.requireBlob
import org.signal.core.util.requireString
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.RegistrationValues
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState

/**
 * Transform non-user friendly store values into less-non-user friendly representations.
 */
object SignalStoreTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == KeyValueDatabase.VALUE
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    return when (cursor.requireString(KeyValueDatabase.KEY)) {
      RegistrationValues.RESTORE_DECISION_STATE -> decodeProto(cursor, RestoreDecisionState.ADAPTER)
      BackupValues.KEY_ARCHIVE_UPLOAD_STATE -> decodeProto(cursor, ArchiveUploadProgressState.ADAPTER)
      else -> DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }
  }

  private fun decodeProto(cursor: Cursor, adapter: ProtoAdapter<*>): String? {
    return cursor.requireBlob(KeyValueDatabase.VALUE)?.let { adapter.decode(it) }?.toString()
  }
}

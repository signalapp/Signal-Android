/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireBlob
import org.signal.core.util.requireString
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.RegistrationValues

/**
 * Transform non-user friendly store values into less-non-user friendly representations.
 */
object SignalStoreTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == KeyValueDatabase.VALUE
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    return when (cursor.requireString(KeyValueDatabase.KEY)) {
      RegistrationValues.RESTORE_DECISION_STATE -> transformRestoreDecisionState(cursor)
      else -> DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }
  }

  private fun transformRestoreDecisionState(cursor: Cursor): String? {
    val restoreDecisionState = cursor.requireBlob(KeyValueDatabase.VALUE)?.let { RestoreDecisionState.ADAPTER.decode(it) }
    return restoreDecisionState.toString()
  }
}

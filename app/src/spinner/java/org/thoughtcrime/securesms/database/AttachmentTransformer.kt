/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.spinner.ColumnTransformer

object AttachmentTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return (tableName == AttachmentTable.TABLE_NAME || tableName == null) && columnName == AttachmentTable.TRANSFER_STATE
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val value = cursor.requireInt(columnName)
    val string = when (value) {
      AttachmentTable.TRANSFER_PROGRESS_DONE -> "DONE"
      AttachmentTable.TRANSFER_PROGRESS_PENDING -> "PENDING"
      AttachmentTable.TRANSFER_PROGRESS_FAILED -> "FAILED"
      AttachmentTable.TRANSFER_PROGRESS_STARTED -> "STARTED"
      AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE -> "PERMANENT_FAILURE"
      else -> "UNKNOWN"
    }
    return "$string ($value)"
  }
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.spinner.ColumnTransformer

object AttachmentTransformer : ColumnTransformer {

  val COLUMNS = setOf(
    AttachmentTable.TRANSFER_STATE,
    AttachmentTable.ARCHIVE_TRANSFER_STATE
  )

  override fun matches(tableName: String?, columnName: String): Boolean {
    return (tableName == AttachmentTable.TABLE_NAME || tableName == null) && columnName in COLUMNS
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String {
    return when (columnName) {
      AttachmentTable.TRANSFER_STATE -> return cursor.toTransferState()
      AttachmentTable.ARCHIVE_TRANSFER_STATE -> return cursor.toArchiveTransferState()
      else -> "UNKNOWN"
    }
  }

  private fun Cursor.toTransferState(): String {
    val value = this.requireInt(AttachmentTable.TRANSFER_STATE)
    val string = when (value) {
      AttachmentTable.TRANSFER_PROGRESS_DONE -> "DONE"
      AttachmentTable.TRANSFER_PROGRESS_PENDING -> "PENDING"
      AttachmentTable.TRANSFER_PROGRESS_FAILED -> "FAILED"
      AttachmentTable.TRANSFER_PROGRESS_STARTED -> "STARTED"
      AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE -> "PERMANENT_FAILURE"
      AttachmentTable.TRANSFER_NEEDS_RESTORE -> "NEEDS_RESTORE"
      AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS -> "RESTORE_IN_PROGRESS"
      AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> "RESTORE_OFFLOADED"
      else -> "UNKNOWN"
    }
    return "$string ($value)"
  }

  private fun Cursor.toArchiveTransferState(): String {
    val state = AttachmentTable.ArchiveTransferState.deserialize(this.requireInt(AttachmentTable.ARCHIVE_TRANSFER_STATE))
    return "${state.name} (${state.value})"
  }
}

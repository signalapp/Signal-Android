package org.thoughtcrime.securesms.calls.log

import androidx.annotation.MainThread

/**
 * Encapsulates a single deletion action
 */
class CallLogStagedDeletion(
  private val stateSnapshot: CallLogSelectionState,
  private val repository: CallLogRepository
) {

  private var isCommitted = false

  fun isStagedForDeletion(id: CallLogRow.Id): Boolean {
    return stateSnapshot.contains(id)
  }

  @MainThread
  fun cancel() {
    isCommitted = true
  }

  @MainThread
  fun commit() {
    if (isCommitted) {
      return
    }

    isCommitted = true
    val callRowIds = stateSnapshot.selected()
      .filterIsInstance<CallLogRow.Id.Call>()
      .map { it.children }
      .flatten()
      .toSet()

    if (stateSnapshot.isExclusionary()) {
      repository.deleteAllCallLogsExcept(callRowIds).subscribe()
    } else {
      repository.deleteSelectedCallLogs(callRowIds).subscribe()
    }
  }
}

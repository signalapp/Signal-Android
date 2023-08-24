package org.thoughtcrime.securesms.calls.log

import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Single

/**
 * Encapsulates a single deletion action
 */
class CallLogStagedDeletion(
  private val filter: CallLogFilter,
  private val stateSnapshot: CallLogSelectionState,
  private val repository: CallLogRepository
) {

  private var isCommitted = false

  /**
   * Returns a Single<Int> which contains the number of failed call-link revocations.
   */
  @MainThread
  fun commit(): Single<Int> {
    if (isCommitted) {
      return Single.just(0)
    }

    isCommitted = true
    val callRowIds = stateSnapshot.selected()
      .filterIsInstance<CallLogRow.Id.Call>()
      .map { it.children }
      .flatten()
      .toSet()

    val callLinkIds = stateSnapshot.selected()
      .filterIsInstance<CallLogRow.Id.CallLink>()
      .map { it.roomId }
      .toSet()

    return when {
      stateSnapshot is CallLogSelectionState.All && filter == CallLogFilter.ALL -> {
        repository.deleteAllCallLogsOnOrBeforeNow()
      }
      stateSnapshot is CallLogSelectionState.Excludes || stateSnapshot is CallLogSelectionState.All -> {
        repository.deleteAllCallLogsExcept(callRowIds, filter == CallLogFilter.MISSED).andThen(
          repository.deleteAllCallLinksExcept(callRowIds, callLinkIds)
        )
      }
      stateSnapshot is CallLogSelectionState.Includes -> {
        repository.deleteSelectedCallLogs(callRowIds).andThen(
          repository.deleteSelectedCallLinks(callRowIds, callLinkIds)
        )
      }
      else -> error("Unhandled state $stateSnapshot $filter")
    }
  }
}

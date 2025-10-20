/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CallQualityScreenViewModel : ViewModel() {

  private val internalState = MutableStateFlow(CallQualitySheetState())
  val state: StateFlow<CallQualitySheetState> = internalState

  fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>) {
    internalState.update { it.copy(selectedQualityIssues = selection) }
  }

  fun onSomethingElseDescriptionChanged(somethingElseDescription: String) {
    internalState.update { it.copy(somethingElseDescription = somethingElseDescription) }
  }

  fun onShareDebugLogChanged(shareDebugLog: Boolean) {
    internalState.update { it.copy(isShareDebugLogSelected = shareDebugLog) }
  }

  fun submit() {
    // Enqueue job.
  }
}

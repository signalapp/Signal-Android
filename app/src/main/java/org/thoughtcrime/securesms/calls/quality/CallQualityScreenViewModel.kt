/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.CallQualitySurveySubmissionJob

class CallQualityScreenViewModel(
  val initialRequest: SubmitCallQualitySurveyRequest
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(CallQualityScreenViewModel::class)
  }

  private val internalState = MutableStateFlow(CallQualitySheetState())
  val state: StateFlow<CallQualitySheetState> = internalState

  fun setUserSatisfiedWithCall(userSatisfiedWithCall: Boolean) {
    internalState.update { it.copy(isUserSatisfiedWithCall = userSatisfiedWithCall) }
  }

  fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>) {
    internalState.update { it.copy(selectedQualityIssues = selection) }
  }

  fun onSomethingElseDescriptionChanged(somethingElseDescription: String) {
    internalState.update { it.copy(somethingElseDescription = somethingElseDescription) }
  }

  fun onShareDebugLogChanged(shareDebugLog: Boolean) {
    internalState.update { it.copy(isShareDebugLogSelected = shareDebugLog) }
  }

  fun clearFailedDueToNetworkAvailability() {
    internalState.update { it.copy(failedDueToNetworkAvailability = false) }
  }

  fun submit() {
    if (!NetworkConstraint.isMet(AppDependencies.application)) {
      Log.w(TAG, "User does not have a network connection. Failing immediately with retry dialog.")
      internalState.update { it.copy(failedDueToNetworkAvailability = true) }
      return
    }

    if (initialRequest.call_type.isEmpty()) {
      Log.i(TAG, "Ignoring survey submission for blank call_type.")
      return
    }

    val stateSnapshot = state.value
    val somethingElseDescription: String? = if (stateSnapshot.selectedQualityIssues.contains(CallQualityIssue.SOMETHING_ELSE)) {
      stateSnapshot.somethingElseDescription.takeIf { it.isNotEmpty() }
    } else {
      null
    }

    val requestToSubmitToJob = initialRequest.newBuilder()
      .user_satisfied(stateSnapshot.isUserSatisfiedWithCall)
      .call_quality_issues(stateSnapshot.selectedQualityIssues.map { it.code })
      .additional_issues_description(somethingElseDescription)
      .build()

    AppDependencies.jobManager.add(CallQualitySurveySubmissionJob(requestToSubmitToJob, stateSnapshot.isShareDebugLogSelected))
  }
}

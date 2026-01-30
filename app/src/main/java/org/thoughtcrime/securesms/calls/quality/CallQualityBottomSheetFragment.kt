/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.util.viewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fragment which manages sheets for walking the user through collecting call
 * quality feedback.
 */
class CallQualityBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "CallQualityBottomSheetRequestKey"

    fun create(request: SubmitCallQualitySurveyRequest): CallQualityBottomSheetFragment {
      return CallQualityBottomSheetFragment().apply {
        arguments = bundleOf(REQUEST_KEY to request.encode())
      }
    }
  }

  private val viewModel: CallQualityScreenViewModel by viewModel {
    val bytes = requireArguments().getByteArray(REQUEST_KEY)!!

    CallQualityScreenViewModel(SubmitCallQualitySurveyRequest.ADAPTER.decode(bytes))
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    SignalStore.callQuality.lastSurveyPromptTime = System.currentTimeMillis().milliseconds
    setFragmentResultListener(CallQualitySomethingElseFragment.REQUEST_KEY) { _, bundle ->
      val result = bundle.getString(CallQualitySomethingElseFragment.REQUEST_KEY) ?: ""

      viewModel.onSomethingElseDescriptionChanged(result)
    }
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CallQualitySheet(
      state = state,
      callback = remember { Callback() }
    )
  }

  private inner class Callback : CallQualitySheetCallback {
    override fun dismiss() {
      this@CallQualityBottomSheetFragment.dismissAllowingStateLoss()
    }

    override fun viewDebugLog() {
      startActivity(
        Intent(requireContext(), SubmitDebugLogActivity::class.java).apply {
          putExtra(SubmitDebugLogActivity.ARG_VIEW_ONLY, true)
        }
      )
    }

    override fun viewDiagnostics() {
      CallQualityDiagnosticsFragment.create(
        viewModel.getRequestSnapshot()
      ).show(parentFragmentManager, null)
    }

    override fun onUserSatisfiedWithCall(isUserSatisfiedWithCall: Boolean) {
      viewModel.setUserSatisfiedWithCall(isUserSatisfiedWithCall)
    }

    override fun describeYourIssue() {
      CallQualitySomethingElseFragment.create(
        viewModel.state.value.somethingElseDescription
      ).show(parentFragmentManager, null)
    }

    override fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>) {
      viewModel.onCallQualityIssueSelectionChanged(selection)
    }

    override fun onShareDebugLogChanged(shareDebugLog: Boolean) {
      viewModel.onShareDebugLogChanged(shareDebugLog)
    }

    override fun submit() {
      viewModel.submit()
      dismiss()
      setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to true))
    }

    override fun tryAgain() {
      viewModel.clearFailedDueToNetworkAvailability()
    }
  }
}

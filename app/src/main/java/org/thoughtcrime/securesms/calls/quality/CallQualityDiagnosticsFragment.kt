/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.compose.ComposeFullScreenDialogFragment

class CallQualityDiagnosticsFragment : ComposeFullScreenDialogFragment() {

  companion object {
    private const val REQUEST_KEY = "CallQualityDiagnosticsRequestKey"

    fun create(request: SubmitCallQualitySurveyRequest): CallQualityDiagnosticsFragment {
      return CallQualityDiagnosticsFragment().apply {
        arguments = bundleOf(REQUEST_KEY to request.encode())
      }
    }
  }

  private val callQualitySurveyRequest: SubmitCallQualitySurveyRequest by lazy {
    val bytes = requireArguments().getByteArray(REQUEST_KEY)!!
    SubmitCallQualitySurveyRequest.ADAPTER.decode(bytes)
  }

  @Composable
  override fun DialogContent() {
    CallQualityDiagnosticsScreen(
      callQualitySurveyRequest = callQualitySurveyRequest,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }
    )
  }
}

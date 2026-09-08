/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Result
import org.signal.core.util.Util
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.signallogin.pdf.SignalLoginPdfRenderer
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen

/**
 * Shows the account and recovery keys that make up the user's Signal Login, the same way registration does.
 */
class SignalLoginViewDetailsFragment : ComposeFragment() {

  companion object {
    private const val CLIPBOARD_TIMEOUT_SECONDS = 60
  }

  private val viewModel: SignalLoginViewDetailsViewModel by viewModels()

  private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
    if (uri != null) {
      val context = requireContext().applicationContext
      lifecycleScope.launch {
        val result = SignalLoginPdfRenderer.renderTo(context, uri, viewModel.state.value.accountKey, viewModel.state.value.recoveryKeyGroups)
        if (result is Result.Failure) {
          Toast.makeText(context, result.failure.userMessageRes, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    SignalLoginViewDetailsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: SignalLoginViewDetailsAction) {
    when (action) {
      SignalLoginViewDetailsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      SignalLoginViewDetailsAction.LaunchSaveToPasswordManager -> {
        lifecycleScope.launch {
          SignalCredentialManager.saveCredential(
            activityContext = requireActivity(),
            username = viewModel.state.value.accountKey,
            password = viewModel.state.value.recoveryKey
          )
        }
      }
      SignalLoginViewDetailsAction.LaunchSaveAsPdf -> savePdfLauncher.launch(SignalLoginPdfRenderer.suggestedFileName(requireContext()))
      is SignalLoginViewDetailsAction.CopyTextToClipboard -> Util.copyToClipboard(requireContext(), action.text, CLIPBOARD_TIMEOUT_SECONDS)
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Result
import org.signal.core.util.Util
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.signallogin.pdf.SignalLoginPdfRenderer
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.DownloadMediaDialog
import org.thoughtcrime.securesms.backup.v2.ui.subscription.KeyLimitExceededDialog
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyDisplayFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel

/**
 * Shows the account and recovery keys that make up the user's Signal Login, the same way registration does.
 */
class SettingsSignalLoginDetailsFragment : ComposeFragment() {

  private val viewModel: SettingsSignalLoginDetailsViewModel by viewModel { SettingsSignalLoginDetailsViewModel(showResetRecoveryKeyButton = true) }

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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setFragmentResultListener(BackupKeyDisplayFragment.AEP_ROTATION_KEY) { _, bundle ->
      if (bundle.getBoolean(BackupKeyDisplayFragment.AEP_ROTATION_KEY, false)) {
        viewModel.onEvent(SettingsSignalLoginDetailsEvent.RecoveryKeyRotated)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resetRecoveryKeyState by viewModel.resetRecoveryKeyState.collectAsStateWithLifecycle()

    TemporaryScreenshotSecurity.bind()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    SignalLoginViewDetailsScreen(
      state = state,
      onEvent = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(it)) }
    )

    when (resetRecoveryKeyState.dialog) {
      ResetRecoveryKeyState.Dialog.NONE -> Unit

      ResetRecoveryKeyState.Dialog.CONFIRMATION -> {
        ResetRecoveryKeyBottomSheet(
          onContinueClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyConfirmed) },
          onDismissRequest = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
        )
      }

      ResetRecoveryKeyState.Dialog.DOWNLOAD_MEDIA -> {
        DownloadMediaDialog(
          onTurnOffAndDownloadClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.TurnOffOptimizedStorageClicked) },
          onCancelClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
        )
      }

      ResetRecoveryKeyState.Dialog.KEY_LIMIT_REACHED -> {
        KeyLimitExceededDialog(
          areBackupsEnabled = resetRecoveryKeyState.areBackupsEnabled,
          onClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
        )
      }
    }
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
      SignalLoginViewDetailsAction.LaunchRecoveryKeyReset -> {
        findNavController().safeNavigate(
          SettingsSignalLoginDetailsFragmentDirections.actionSettingsSignalLoginDetailsFragmentToBackupKeyDisplayFragment().setStartWithKeyRotation(true)
        )
      }
      is SignalLoginViewDetailsAction.CopyTextToClipboard -> Util.copyToClipboardSensitive(requireContext(), action.text)
    }
  }
}

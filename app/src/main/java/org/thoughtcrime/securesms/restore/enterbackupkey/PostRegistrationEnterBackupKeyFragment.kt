/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.enterbackupkey

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dialogs
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.restore.EnterBackupKeyScreen
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.AccountEntropyPool

/**
 * Collect user's [AccountEntropyPool] string for use in a post-registration manual remote backup restore flow.
 */
class PostRegistrationEnterBackupKeyFragment : ComposeFragment() {
  companion object {
    private val TAG = Log.tag(PostRegistrationEnterBackupKeyFragment::class)
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val viewModel by viewModels<PostRegistrationEnterBackupKeyViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val successful = viewModel
          .state
          .map { it.restoreBackupTierSuccessful }
          .filter { it }
          .firstOrNull() ?: false

        if (successful) {
          Log.i(TAG, "Successfully restored an AEP, moving to remote restore")
          findNavController().safeNavigate(PostRegistrationEnterBackupKeyFragmentDirections.goToRemoteRestoreActivity())
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnterBackupKeyScreen(
      isDisplayedDuringManualRestore = false,
      backupKey = viewModel.backupKey,
      isBackupKeyValid = state.backupKeyValid,
      inProgress = state.inProgress,
      chunkLength = 4,
      aepValidationError = state.aepValidationError,
      onBackupKeyChanged = viewModel::updateBackupKey,
      onNextClicked = { viewModel.restoreBackupTimestamp() },
      onLearnMore = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onSkip = { findNavController().popBackStack() }
    ) {
      ErrorContent(
        errorDialog = state.errorDialog,
        onBackupKeyHelp = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
        onDismiss = viewModel::hideErrorDialog
      )
    }
  }
}

@Composable
private fun ErrorContent(
  errorDialog: PostRegistrationEnterBackupKeyViewModel.ErrorDialog?,
  onBackupKeyHelp: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  if (errorDialog == null) {
    return
  }

  when (errorDialog) {
    PostRegistrationEnterBackupKeyViewModel.ErrorDialog.AEP_INVALID -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_incorrect_backup_key_title),
        body = stringResource(R.string.EnterBackupKey_incorrect_backup_key_message),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
        onConfirm = {},
        onDeny = onBackupKeyHelp,
        onDismiss = onDismiss
      )
    }

    PostRegistrationEnterBackupKeyViewModel.ErrorDialog.BACKUP_NOT_FOUND -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_backup_not_found),
        body = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
        onConfirm = {},
        onDeny = onBackupKeyHelp,
        onDismiss = onDismiss
      )
    }

    PostRegistrationEnterBackupKeyViewModel.ErrorDialog.UNKNOWN_ERROR -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
        body = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        onConfirm = {},
        onDismiss = onDismiss
      )
    }

    PostRegistrationEnterBackupKeyViewModel.ErrorDialog.BACKUPS_NOT_ENABLED -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_backup_not_found),
        body = stringResource(R.string.EnterBackupKey_backup_key_incorrect_or_backups_not_enabled),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
        onConfirm = {},
        onDeny = onBackupKeyHelp,
        onDismiss = onDismiss
      )
    }

    PostRegistrationEnterBackupKeyViewModel.ErrorDialog.RATE_LIMITED -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_backup_not_found),
        body = stringResource(R.string.EnterBackupKey_backup_key_check_rate_limited),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
        onConfirm = {},
        onDeny = onBackupKeyHelp,
        onDismiss = onDismiss
      )
    }
  }
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registrationv3.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Enter backup key screen for manual Signal Backups restore flow.
 */
class EnterBackupKeyFragment : ComposeFragment() {

  companion object {
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel by viewModels<EnterBackupKeyViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .filter { it.registrationCheckpoint == RegistrationCheckpoint.BACKUP_TIER_NOT_RESTORED }
          .collect {
            viewModel.handleBackupTierNotRestored()
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()

    EnterBackupKeyScreen(
      backupKey = viewModel.backupKey,
      inProgress = sharedState.inProgress,
      isBackupKeyValid = state.backupKeyValid,
      chunkLength = state.chunkLength,
      aepValidationError = state.aepValidationError,
      onBackupKeyChanged = viewModel::updateBackupKey,
      onNextClicked = {
        viewModel.registering()
        sharedViewModel.registerWithBackupKey(
          context = requireContext(),
          backupKey = viewModel.backupKey,
          e164 = null,
          pin = null
        )
      },

      onLearnMore = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onSkip = {
        sharedViewModel.skipRestore()
        findNavController().safeNavigate(EnterBackupKeyFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
      },
      dialogContent = {
        if (state.showStorageAccountRestoreProgress) {
          Dialogs.IndeterminateProgressDialog()
        } else {
          ErrorContent(
            state = state,
            onBackupTierRetry = { sharedViewModel.restoreBackupTier() },
            onSkipRestoreAfterRegistration = {
              viewLifecycleOwner.lifecycleScope.launch {
                sharedViewModel.skipRestore()
                viewModel.performStorageServiceAccountRestoreIfNeeded()
                sharedViewModel.resumeNormalRegistration()
              }
            },
            onBackupTierNotRestoredDismiss = viewModel::hideRestoreBackupKeyFailed,
            onRegistrationErrorDismiss = viewModel::clearRegistrationError,
            onBackupKeyHelp = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) }
          )
        }
      }
    )
  }
}

@Composable
private fun ErrorContent(
  state: EnterBackupKeyViewModel.EnterBackupKeyState,
  onBackupTierRetry: () -> Unit = {},
  onSkipRestoreAfterRegistration: () -> Unit = {},
  onBackupTierNotRestoredDismiss: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {}
) {
  if (state.showBackupTierNotRestoreError) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.EnterBackupKey_backup_not_found),
      body = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
      confirm = stringResource(R.string.EnterBackupKey_try_again),
      dismiss = stringResource(R.string.EnterBackupKey_skip_restore),
      onConfirm = onBackupTierRetry,
      onDeny = onSkipRestoreAfterRegistration,
      onDismiss = onBackupTierNotRestoredDismiss
    )
  } else if (state.showRegistrationError) {
    if (state.registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword) {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_incorrect_backup_key_title),
        body = stringResource(R.string.EnterBackupKey_incorrect_backup_key_message),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
        onConfirm = {},
        onDeny = onBackupKeyHelp,
        onDismiss = onRegistrationErrorDismiss
      )
    } else {
      val message = when (state.registerAccountResult) {
        is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
        else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onRegistrationErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

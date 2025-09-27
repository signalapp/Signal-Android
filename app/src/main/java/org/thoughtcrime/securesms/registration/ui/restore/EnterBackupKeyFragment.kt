/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

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
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialog
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel
import org.thoughtcrime.securesms.components.contactsupport.SendSupportEmailEffect
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
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
  private val contactSupportViewModel: ContactSupportViewModel<Unit> by viewModels()

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
          .filter { it.registrationCheckpoint == RegistrationCheckpoint.BACKUP_TIMESTAMP_NOT_RESTORED }
          .collect {
            viewModel.handleBackupTimestampNotRestored()
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val contactSupportState: ContactSupportViewModel.ContactSupportState<Unit> by contactSupportViewModel.state.collectAsStateWithLifecycle()

    SendSupportEmailEffect(
      contactSupportState = contactSupportState,
      subjectRes = { R.string.EnterBackupKey_network_failure_support_email },
      filterRes = { R.string.EnterBackupKey_network_failure_support_email_filter }
    ) {
      contactSupportViewModel.hideContactSupport()
    }

    EnterBackupKeyScreen(
      isDisplayedDuringManualRestore = true,
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
          pin = null,
          aciIdentityKeyPair = null,
          pniIdentityKeyPair = null
        )
      },
      onLearnMore = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onSkip = {
        sharedViewModel.skipRestore()
        findNavController().safeNavigate(EnterBackupKeyFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
      },
      dialogContent = {
        if (contactSupportState.show) {
          ContactSupportDialog(
            showInProgress = contactSupportState.showAsProgress,
            callbacks = contactSupportViewModel
          )
        } else {
          ErrorContent(
            state = state,
            onBackupTierRetry = {
              viewModel.incrementBackupTierRetry()
              sharedViewModel.checkForBackupFile()
            },
            onAbandonRemoteRestoreAfterRegistration = {
              viewLifecycleOwner.lifecycleScope.launch {
                sharedViewModel.resumeNormalRegistration()
              }
            },
            onBackupTierNotRestoredDismiss = viewModel::hideRestoreBackupKeyFailed,
            onContactSupport = { contactSupportViewModel.showContactSupport() },
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
  onAbandonRemoteRestoreAfterRegistration: () -> Unit = {},
  onBackupTierNotRestoredDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {}
) {
  if (state.showBackupTierNotRestoreError == EnterBackupKeyViewModel.TierRestoreError.NETWORK_ERROR) {
    if (state.tierRetryAttempts > 1) {
      Dialogs.AdvancedAlertDialog(
        title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
        body = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
        positive = stringResource(R.string.EnterBackupKey_try_again),
        neutral = stringResource(R.string.EnterBackupKey_contact_support),
        negative = stringResource(android.R.string.cancel),
        onPositive = {
          onBackupTierNotRestoredDismiss()
          onBackupTierRetry()
        },
        onNeutral = {
          onBackupTierNotRestoredDismiss()
          onContactSupport()
        },
        onNegative = {
          onBackupTierNotRestoredDismiss()
          onAbandonRemoteRestoreAfterRegistration()
        }
      )
    } else {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
        body = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
        confirm = stringResource(R.string.EnterBackupKey_try_again),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = onBackupTierRetry,
        onDeny = onAbandonRemoteRestoreAfterRegistration,
        onDismiss = onBackupTierNotRestoredDismiss,
        onDismissRequest = {}
      )
    }
  } else if (state.showBackupTierNotRestoreError == EnterBackupKeyViewModel.TierRestoreError.NOT_FOUND) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.EnterBackupKey_backup_not_found),
      body = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
      confirm = stringResource(R.string.EnterBackupKey_try_again),
      dismiss = stringResource(R.string.EnterBackupKey_skip_restore),
      onConfirm = onBackupTierRetry,
      onDeny = onAbandonRemoteRestoreAfterRegistration,
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

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.payments.backup.PaymentsRecoveryStartFragmentArgs.Builder
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.pin.PinOptOutDialog

/**
 * Fragment which allows user to enable or disable their PIN
 */
class AdvancedPinSettingsFragment : ComposeFragment() {

  private val viewModel: AdvancedPinSettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.event.collectLatest {
          when (it) {
            AdvancedPinSettingsViewModel.Event.SHOW_OPT_OUT_DIALOG -> PinOptOutDialog.show(requireContext()) {
              viewModel.onPinOptOutSuccess()
              displayOptOutSnackbar()
            }
            AdvancedPinSettingsViewModel.Event.LAUNCH_PIN_CREATION_FLOW -> {
              startActivityForResult(CreateSvrPinActivity.getIntentForPinCreate(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN)
            }
            AdvancedPinSettingsViewModel.Event.LAUNCH_RECOVERY_PHRASE_HANDLING -> {
              val intent = Intent(requireContext(), PaymentsActivity::class.java)
              intent.putExtra(PaymentsActivity.EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_paymentsBackup)
              intent.putExtra(PaymentsActivity.EXTRA_STARTING_ARGUMENTS, Builder().setFinishOnConfirm(true).build().toBundle())

              startActivity(intent)
            }
          }
        }
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN && resultCode == CreateSvrPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_created, Snackbar.LENGTH_LONG).show()
    }
  }

  @Composable
  override fun FragmentContent() {
    val hasOptedOutOfPin: Boolean by viewModel.hasOptedOutOfPin.collectAsStateWithLifecycle()

    AdvancedPinSettingsFragmentContent(
      hasOptedOutOfPin = hasOptedOutOfPin,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      snackbarHostState = viewModel.snackbarHostState,
      onOptionClick = viewModel::setOptOut
    )

    val dialog: AdvancedPinSettingsViewModel.Dialog by viewModel.dialog.collectAsStateWithLifecycle()

    when (dialog) {
      AdvancedPinSettingsViewModel.Dialog.REGISTRATION_LOCK -> PinsAreRequiredForRegistrationLockDialog(
        onDismiss = {
          viewModel.dismissDialog()
        }
      )
      AdvancedPinSettingsViewModel.Dialog.RECORD_PAYMENTS_RECOVERY_PHRASE -> RecordPaymentsRecoveryPhraseDialog(
        onConfirm = {
          viewModel.launchRecoveryPhraseHandling()
        },
        onDismiss = {
          viewModel.dismissDialog()
        }
      )
      else -> Unit
    }
  }

  private fun displayOptOutSnackbar() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.snackbarHostState.showSnackbar(
        message = getString(R.string.ApplicationPreferencesActivity_pin_disabled),
        duration = SnackbarDuration.Long
      )
    }
  }
}

@Composable
private fun AdvancedPinSettingsFragmentContent(
  hasOptedOutOfPin: Boolean,
  onNavigationClick: () -> Unit = {},
  onOptionClick: (Boolean) -> Unit = {},
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__advanced_pin_settings_title),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
    onNavigationClick = onNavigationClick,
    snackbarHost = {
      Snackbars.Host(
        snackbarHostState = snackbarHostState
      )
    }
  ) {
    val listener: () -> Unit = remember(onOptionClick, hasOptedOutOfPin) {
      {
        onOptionClick(hasOptedOutOfPin)
      }
    }

    if (hasOptedOutOfPin) {
      Rows.TextRow(
        text = stringResource(R.string.preferences__enable_pin),
        label = stringResource(R.string.preferences__pins_keep_information_stored_with_signal_encrypted_so_only_you_can_access_it),
        onClick = listener,
        modifier = Modifier.padding(it)
      )
    } else {
      Rows.TextRow(
        text = stringResource(R.string.preferences__disable_pin),
        label = stringResource(R.string.preferences__if_you_disable_the_pin_you_will_lose_all_data),
        onClick = listener,
        modifier = Modifier.padding(it)
      )
    }
  }
}

@Composable
private fun PinsAreRequiredForRegistrationLockDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleMessageDialog(
    message = stringResource(R.string.ApplicationPreferencesActivity_pins_are_required_for_registration_lock),
    dismiss = stringResource(android.R.string.ok),
    onDismiss = onDismiss
  )
}

@Composable
private fun RecordPaymentsRecoveryPhraseDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.ApplicationPreferencesActivity_record_payments_recovery_phrase),
    body = stringResource(R.string.ApplicationPreferencesActivity_before_you_can_disable_your_pin),
    confirm = stringResource(R.string.ApplicationPreferencesActivity_record_phrase),
    onConfirm = onConfirm,
    dismiss = stringResource(android.R.string.cancel),
    onDismiss = onDismiss
  )
}

@SignalPreview
@Composable
private fun AdvancedPinSettingsFragmentContentEnabledPreview() {
  Previews.Preview {
    AdvancedPinSettingsFragmentContent(
      hasOptedOutOfPin = false
    )
  }
}

@SignalPreview
@Composable
private fun AdvancedPinSettingsFragmentContentDisabledPreview() {
  Previews.Preview {
    AdvancedPinSettingsFragmentContent(
      hasOptedOutOfPin = true
    )
  }
}

@SignalPreview
@Composable
private fun PinsAreRequiredForRegistrationLockDialogPreview() {
  Previews.Preview {
    PinsAreRequiredForRegistrationLockDialog()
  }
}

@SignalPreview
@Composable
private fun RecordPaymentsRecoveryPhraseDialogPreview() {
  Previews.Preview {
    RecordPaymentsRecoveryPhraseDialog({}, {})
  }
}

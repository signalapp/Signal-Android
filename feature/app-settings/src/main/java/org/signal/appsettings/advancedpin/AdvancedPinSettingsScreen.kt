/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object AdvancedPinSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_PIN_TOGGLE = "row-pin-toggle"
}

/**
 * Turns the Signal PIN on or off, walking the user through whatever has to happen first.
 */
@Composable
fun AdvancedPinSettingsScreen(
  state: AdvancedPinSettingsState,
  onEvent: (AdvancedPinSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(AdvancedPinSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.preferences__advanced_pin_settings_title),
    onNavigationClick = { onEvent(AdvancedPinSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(AdvancedPinSettingsEvent.SnackbarDismissed) },
        duration = Snackbars.Duration.LONG
      )
    },
    modifier = modifier,
    expandableTitle = true
  ) { contentPadding ->
    AppSettingsLazyColumn(
      scaffoldPadding = contentPadding,
      modifier = Modifier.testTag(AdvancedPinSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.TextRow(
          text = stringResource(if (state.hasOptedOutOfPin) R.string.preferences__enable_pin else R.string.preferences__disable_pin),
          label = stringResource(
            if (state.hasOptedOutOfPin) {
              R.string.preferences__pins_keep_information_stored_with_signal_encrypted_so_only_you_can_access_it
            } else {
              R.string.preferences__if_you_disable_the_pin_you_will_lose_all_data
            }
          ),
          onClick = { onEvent(AdvancedPinSettingsEvent.PinToggleClicked) },
          modifier = Modifier.testTag(AdvancedPinSettingsTestTags.ROW_PIN_TOGGLE)
        )
      }
    }
  }

  when (state.dialog) {
    AdvancedPinSettingsState.Dialog.None -> Unit
    AdvancedPinSettingsState.Dialog.RegistrationLockRequiresPin -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.ApplicationPreferencesActivity_pins_are_required_for_registration_lock),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(AdvancedPinSettingsEvent.DialogDismissed) }
      )
    }
    AdvancedPinSettingsState.Dialog.RecordPaymentsRecoveryPhrase -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.ApplicationPreferencesActivity_record_payments_recovery_phrase),
        body = stringResource(R.string.ApplicationPreferencesActivity_before_you_can_disable_your_pin),
        confirm = stringResource(R.string.ApplicationPreferencesActivity_record_phrase),
        onConfirm = { onEvent(AdvancedPinSettingsEvent.RecordRecoveryPhraseConfirmed) },
        dismiss = stringResource(android.R.string.cancel),
        onDeny = { onEvent(AdvancedPinSettingsEvent.DialogDismissed) },
        onDismissRequest = { onEvent(AdvancedPinSettingsEvent.DialogDismissed) }
      )
    }
    AdvancedPinSettingsState.Dialog.RotateBackupKey -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.AdvancedPinSettingsFragment_rotate_aep_dialog_title),
        body = stringResource(R.string.AdvancedPinSettingsFragment_rotate_aep_dialog_body),
        confirm = stringResource(R.string.AdvancedPinSettingsFragment_rotate_aep_dialog_positive_button),
        onConfirm = { onEvent(AdvancedPinSettingsEvent.RotateBackupKeyConfirmed) },
        dismiss = stringResource(android.R.string.cancel),
        onDeny = { onEvent(AdvancedPinSettingsEvent.DialogDismissed) },
        onDismissRequest = { onEvent(AdvancedPinSettingsEvent.DialogDismissed) }
      )
    }
  }
}

@Composable
private fun AdvancedPinSettingsState.Snackbar.message(): String? {
  return when (this) {
    AdvancedPinSettingsState.Snackbar.None -> null
    AdvancedPinSettingsState.Snackbar.PinCreated -> stringResource(R.string.ApplicationPreferencesActivity_pin_created)
    AdvancedPinSettingsState.Snackbar.PinDisabled -> stringResource(R.string.ApplicationPreferencesActivity_pin_disabled)
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AdvancedPinSettingsScreenPinOnPreview() {
  AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(hasOptedOutOfPin = false), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AdvancedPinSettingsScreenPinOffPreview() {
  AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(hasOptedOutOfPin = true), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AdvancedPinSettingsScreenRotateKeyPreview() {
  AdvancedPinSettingsScreen(state = AdvancedPinSettingsState(dialog = AdvancedPinSettingsState.Dialog.RotateBackupKey), onEvent = {})
}

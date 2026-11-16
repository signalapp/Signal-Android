/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.advancedpin.AdvancedPinSettingsAction
import org.signal.appsettings.advancedpin.AdvancedPinSettingsEvent
import org.signal.appsettings.advancedpin.AdvancedPinSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.navigation.ResultEffect
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.payments.backup.PaymentsRecoveryStartFragmentArgs
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.pin.PinOptOutDialog

/**
 * The advanced PIN settings, wired to their view model and to the PIN, payments and opt-out flows they open. A
 * [BackupKeyRotated] result from the backup key screen lets turning the PIN off go ahead.
 */
@Composable
fun AdvancedPinSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val viewModel: AdvancedPinSettingsViewModel = viewModel { AdvancedPinSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  val pinCreationLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      viewModel.onEvent(AdvancedPinSettingsEvent.PinCreated)
    }
  }

  ResultEffect<BackupKeyRotated> {
    viewModel.onEvent(AdvancedPinSettingsEvent.BackupKeyRotated)
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      AdvancedPinSettingsAction.ConfirmPinOptOut -> PinOptOutDialog.show(context, lifecycleOwner, true) {
        viewModel.onEvent(AdvancedPinSettingsEvent.PinOptedOut)
      }
      AdvancedPinSettingsAction.LaunchPinCreation -> pinCreationLauncher.launch(CreateSvrPinActivity.getIntentForPinCreate(context))
      AdvancedPinSettingsAction.LaunchRecoveryPhraseRecording -> {
        val intent = Intent(context, PaymentsActivity::class.java)
          .putExtra(PaymentsActivity.EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_paymentsBackup)
          .putExtra(PaymentsActivity.EXTRA_STARTING_ARGUMENTS, PaymentsRecoveryStartFragmentArgs.Builder().setFinishOnConfirm(true).build().toBundle())
        context.startActivity(intent)
      }
    }
  }

  AdvancedPinSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

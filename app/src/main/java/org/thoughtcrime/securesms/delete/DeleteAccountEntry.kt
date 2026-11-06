/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.deleteaccount.DeleteAccountAction
import org.signal.appsettings.deleteaccount.DeleteAccountEvent
import org.signal.appsettings.deleteaccount.DeleteAccountWithNumberScreen
import org.signal.appsettings.deleteaccount.DeleteAccountWithoutNumberScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.navigation.ResultEffect

/**
 * Account deletion, showing whichever of the two screens fits the account, wired to its view model. The country the
 * user picks comes back as a [DeleteAccountCountrySelected] result.
 */
@Composable
fun DeleteAccountEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: DeleteAccountViewModel = viewModel { DeleteAccountViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  ResultEffect<DeleteAccountCountrySelected> { result ->
    viewModel.onEvent(DeleteAccountEvent.CountrySelected(result.regionCode))
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      DeleteAccountAction.LaunchAppSettings -> context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
        }
      )
    }
  }

  if (state.hasPhoneNumber) {
    DeleteAccountWithNumberScreen(state = state, onEvent = viewModel::onEvent)
  } else {
    DeleteAccountWithoutNumberScreen(state = state, onEvent = viewModel::onEvent)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.totpsetup.TotpSetupAction
import org.signal.appsettings.totpsetup.TotpSetupEvent
import org.signal.appsettings.totpsetup.TotpSetupScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.Util
import org.signal.core.util.logging.Log

private const val TAG = "TotpSetupEntry"

/**
 * Authenticator app setup, wired to its view model and to the authenticator app and clipboard it hands the key to.
 */
@Composable
fun TotpSetupEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: TotpSetupViewModel = viewModel { TotpSetupViewModel(parentEventEmitter = parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is TotpSetupAction.LaunchTotpApp -> {
        try {
          context.startActivity(Intent(Intent.ACTION_VIEW, action.uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
          Log.w(TAG, "No app is willing to handle the authenticator setup link.")
          viewModel.onEvent(TotpSetupEvent.NoTotpAppFound)
        }
      }
      is TotpSetupAction.CopyKeyToClipboard -> Util.copyToClipboardSensitive(context, action.key)
    }
  }

  TotpSetupScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

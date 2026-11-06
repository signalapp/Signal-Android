/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkey

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.signal.appsettings.passkeysetup.PasskeySetupAction
import org.signal.appsettings.passkeysetup.PasskeySetupEvent
import org.signal.appsettings.passkeysetup.PasskeySetupScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.PasskeyCreationResult
import org.signal.passwordmanager.SignalPasskeyManager
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.appsettings.R as AppSettingsR

private const val TAG = "PasskeySetupEntry"

/**
 * Passkey setup, wired to its view model and to the passkey provider sheet the ceremony puts on screen.
 */
@Composable
fun PasskeySetupEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val activity = requireNotNull(LocalActivity.current)
  val scope = rememberCoroutineScope()
  val defaultPasskeyName = stringResource(AppSettingsR.string.AccountSettingsRepository__passkey)
  val viewModel: PasskeySetupViewModel = viewModel {
    PasskeySetupViewModel(
      parentEventEmitter = parentEventEmitter,
      repository = PasskeySetupRepository(defaultPasskeyName = defaultPasskeyName)
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is PasskeySetupAction.OpenSupportArticle -> CommunicationActions.openBrowserLink(context, action.url)
      is PasskeySetupAction.CreatePasskey -> scope.launch {
        val parameters = action.parameters
        val result = try {
          SignalPasskeyManager.createPasskey(
            activityContext = activity,
            rpId = parameters.relyingPartyId,
            rpName = parameters.relyingPartyName,
            userHandle = parameters.userHandle,
            userName = parameters.userName,
            allowedAlgorithms = parameters.allowedAlgorithms,
            excludeCredentialIds = parameters.excludeCredentialIds
          )
        } catch (e: CancellationException) {
          Log.w(TAG, "The passkey ceremony was canceled before it could finish.", e)
          viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.UserCanceled))
          throw e
        }

        viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(result))
      }
    }
  }

  PasskeySetupScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.account.AccountSettingsAction
import org.signal.appsettings.account.AccountSettingsEvent
import org.signal.appsettings.account.AccountSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.ServiceUtil
import org.signal.passwordmanager.SignalPasskeyManager
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.registration.ui.RegistrationIntents
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.signal.appsettings.R as AppSettingsR

/**
 * Account settings on a primary device, wired to their view model and to the PIN flow, screen lock prompts and other
 * activities they open.
 */
@Composable
fun AccountSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: AccountSettingsViewModel = viewModel {
    AccountSettingsViewModel(
      parentEventEmitter = parentEventEmitter,
      accountRepository = AccountSettingsRepository(),
      arePasskeysSupported = SignalPasskeyManager.isSupported(context)
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()

  val pinFlowLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      viewModel.onEvent(AccountSettingsEvent.PinCreated)
    }
  }

  val addMethodBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(AppSettingsR.string.AccountSettingsFragment__unlock_to_add_two_factor_method),
    educationSheetMessage = stringResource(AppSettingsR.string.AccountSettingsFragment__to_add_this_method_confirm_its_you),
    onAuthenticationFailed = { viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed) }
  )

  val removalBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(AppSettingsR.string.AccountSettingsFragment__unlock_to_remove_two_factor_method),
    educationSheetMessage = stringResource(AppSettingsR.string.AccountSettingsFragment__to_remove_this_method_confirm_its_you),
    onAuthenticationFailed = { viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed) }
  )

  val signalLoginBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(AppSettingsR.string.AccountSettingsFragment__unlock_to_view_signal_login),
    educationSheetMessage = stringResource(AppSettingsR.string.AccountSettingsFragment__to_view_your_signal_login_confirm_its_you),
    onAuthenticationFailed = { viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed) }
  )

  val deleteAccountBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(AppSettingsR.string.AccountSettingsFragment__unlock_to_confirm_its_you),
    educationSheetMessage = stringResource(AppSettingsR.string.AccountSettingsFragment__to_delete_your_account_confirm_its_you),
    onAuthenticationFailed = { viewModel.onEvent(AccountSettingsEvent.AuthenticationFailed) }
  )

  CollectActions(viewModel.actions) { action ->
    when (action) {
      AccountSettingsAction.LaunchCreatePinFlow -> pinFlowLauncher.launch(CreateSvrPinActivity.getIntentForPinCreate(context))
      AccountSettingsAction.LaunchChangePinFlow -> pinFlowLauncher.launch(CreateSvrPinActivity.getIntentForPinChangeFromSettings(context))
      AccountSettingsAction.AuthenticateToViewSignalLoginDetails -> signalLoginBiometrics.withBiometricsAuthentication {
        viewModel.onEvent(AccountSettingsEvent.SignalLoginDetailsAuthenticated)
      }
      AccountSettingsAction.AuthenticateToAddTotpApp -> addMethodBiometrics.withBiometricsAuthentication {
        viewModel.onEvent(AccountSettingsEvent.AddTotpAppAuthenticated)
      }
      AccountSettingsAction.AuthenticateToAddPasskey -> addMethodBiometrics.withBiometricsAuthentication {
        viewModel.onEvent(AccountSettingsEvent.AddPasskeyAuthenticated)
      }
      is AccountSettingsAction.AuthenticateToRemoveMethod -> removalBiometrics.withBiometricsAuthentication {
        viewModel.onEvent(AccountSettingsEvent.MethodRemovalAuthenticated(action.method))
      }
      is AccountSettingsAction.OpenSupportArticle -> CommunicationActions.openBrowserLink(context, action.url)
      AccountSettingsAction.OpenPlayStore -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      AccountSettingsAction.LaunchReRegistration -> context.startActivity(RegistrationIntents.newIntentForReRegistration(context))
      AccountSettingsAction.AuthenticateToDeleteAccount -> deleteAccountBiometrics.withBiometricsAuthentication {
        viewModel.onEvent(AccountSettingsEvent.DeleteAccountAuthenticated)
      }
      AccountSettingsAction.WipeAllData -> {
        if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
          viewModel.onEvent(AccountSettingsEvent.DataWipeFailed)
        }
      }
    }
  }

  AccountSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

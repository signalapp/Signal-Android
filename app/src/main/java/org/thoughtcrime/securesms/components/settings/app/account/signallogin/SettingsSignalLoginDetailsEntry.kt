/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.navigation.ResultEffect
import org.signal.core.util.Result
import org.signal.core.util.Util
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.signallogin.pdf.SignalLoginPdfRenderer
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.DownloadMediaDialog
import org.thoughtcrime.securesms.backup.v2.ui.subscription.KeyLimitExceededDialog
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.signallogin.R as SignalLoginR

/**
 * The account and recovery keys that make up the user's Signal Login, as reached from account settings, wired to the
 * password manager, PDF export and recovery key reset. A finished reset comes back as a [BackupKeyRotated] result.
 */
@Composable
fun SettingsSignalLoginDetailsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val activity = requireNotNull(LocalActivity.current)
  val scope = rememberCoroutineScope()
  val supportUrl = stringResource(R.string.recovery_key_phishing_support_url)
  val viewModel: SettingsSignalLoginDetailsViewModel = viewModel {
    SettingsSignalLoginDetailsViewModel(
      parentEventEmitter = parentEventEmitter,
      showResetRecoveryKeyButton = true,
      isPasswordManagerAvailable = SignalCredentialManager.isSupported(context),
      isGooglePasswordManagerDefault = SignalCredentialManager.isGooglePasswordManagerDefault(context)
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val resetRecoveryKeyState by viewModel.resetRecoveryKeyState.collectAsStateWithLifecycle()
  val snackbar by viewModel.snackbar.collectAsStateWithLifecycle()

  TemporaryScreenshotSecurity.bind()

  ResultEffect<BackupKeyRotated> {
    viewModel.onEvent(SettingsSignalLoginDetailsEvent.RecoveryKeyRotated)
  }

  val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    if (uri != null) {
      val appContext = context.applicationContext
      scope.launch {
        val result = SignalLoginPdfRenderer.renderTo(appContext, uri, viewModel.state.value.accountKey, viewModel.state.value.recoveryKeyGroups)
        if (result is Result.Failure) {
          viewModel.onEvent(SettingsSignalLoginDetailsEvent.SaveAsPdfFailed(result.failure.userMessageRes))
        }
      }
    }
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      SignalLoginViewDetailsAction.LaunchSaveToPasswordManager -> scope.launch {
        SignalCredentialManager.saveCredential(
          activityContext = activity,
          username = viewModel.state.value.accountKey,
          password = viewModel.state.value.recoveryKey
        )
      }
      SignalLoginViewDetailsAction.LaunchSaveAsPdf -> savePdfLauncher.launch(SignalLoginPdfRenderer.suggestedFileName(context))
      SignalLoginViewDetailsAction.OpenDoNotShareLearnMoreArticle -> CommunicationActions.openBrowserLink(context, supportUrl)
      is SignalLoginViewDetailsAction.CopyTextToClipboard -> Util.copyToClipboardSensitive(context, action.text)
      SignalLoginViewDetailsAction.NavigateBack,
      SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable -> error("Handled by the view model, never sent as an action: $action")
    }
  }

  SignalLoginViewDetailsScreen(
    state = state,
    onEvent = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.Screen(it)) },
    snackbarHost = {
      Snackbars.Host(
        message = snackbar.message(),
        onDismissed = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.SnackbarDismissed) },
        duration = Snackbars.Duration.LONG
      )
    }
  )

  when (resetRecoveryKeyState.dialog) {
    ResetRecoveryKeyState.Dialog.NONE -> Unit

    ResetRecoveryKeyState.Dialog.CONFIRMATION -> {
      ResetRecoveryKeyBottomSheet(
        onContinueClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyConfirmed) },
        onDismissRequest = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
      )
    }

    ResetRecoveryKeyState.Dialog.DOWNLOAD_MEDIA -> {
      DownloadMediaDialog(
        onTurnOffAndDownloadClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.TurnOffOptimizedStorageClicked) },
        onCancelClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
      )
    }

    ResetRecoveryKeyState.Dialog.KEY_LIMIT_REACHED -> {
      KeyLimitExceededDialog(
        areBackupsEnabled = resetRecoveryKeyState.areBackupsEnabled,
        onClick = { viewModel.onEvent(SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed) }
      )
    }
  }
}

@Composable
private fun SettingsSignalLoginDetailsSnackbar.message(): String? {
  return when (this) {
    SettingsSignalLoginDetailsSnackbar.None -> null
    SettingsSignalLoginDetailsSnackbar.NoPasswordManagerAvailable -> stringResource(SignalLoginR.string.SignalLoginViewDetailsScreen__no_password_manager_available)
    is SettingsSignalLoginDetailsSnackbar.SaveAsPdfFailed -> stringResource(messageRes)
  }
}

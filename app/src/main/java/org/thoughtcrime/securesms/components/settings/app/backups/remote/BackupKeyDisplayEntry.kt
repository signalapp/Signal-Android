/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.DownloadMediaDialog
import org.thoughtcrime.securesms.backup.v2.ui.subscription.KeyLimitExceededDialog
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.compose.Nav

/**
 * Shows the user their backup key, and when [startWithKeyRotation] is set, generates and confirms a replacement for it.
 * [onKeyRotated] is called once a new key is committed, and [onClose] whenever the screen should go away without one.
 */
@Composable
fun BackupKeyDisplayEntry(
  startWithKeyRotation: Boolean,
  onKeyRotated: () -> Unit,
  onClose: () -> Unit
) {
  val viewModel: BackupKeyDisplayViewModel = viewModel { BackupKeyDisplayViewModel() }
  val currentOnKeyRotated by rememberUpdatedState(onKeyRotated)
  val currentOnClose by rememberUpdatedState(onClose)

  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(Unit) {
    navController.setLifecycleOwner(lifecycleOwner)
    navController.enableOnBackPressed(true)
  }

  LaunchedEffect(startWithKeyRotation, state.rotationState) {
    if (startWithKeyRotation && state.rotationState == BackupKeyRotationState.NOT_STARTED) {
      viewModel.rotateBackupKey()
    }
  }

  LaunchedEffect(state.rotationState) {
    if (state.rotationState == BackupKeyRotationState.FINISHED) {
      currentOnKeyRotated()
    }
  }

  val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  var displayWarningDialog by remember { mutableStateOf(false) }
  BackHandler(enabled = state.rotationState == BackupKeyRotationState.USER_VERIFICATION) {
    displayWarningDialog = true
  }

  val mode = remember(state.rotationState, state.canRotateKey, state.areBackupsEnabled) {
    if (state.rotationState == BackupKeyRotationState.NOT_STARTED) {
      MessageBackupsKeyRecordMode.CreateNewKey(
        onCreateNewKeyClick = {
          viewModel.rotateBackupKey()
        },
        onTurnOffAndDownloadClick = {
          viewModel.turnOffOptimizedStorageAndDownloadMedia()
          currentOnClose()
        },
        isOptimizedStorageEnabled = state.isOptimizedStorageEnabled,
        canRotateKey = state.canRotateKey,
        areBackupsEnabled = state.areBackupsEnabled
      )
    } else {
      MessageBackupsKeyRecordMode.Next(
        onNextClick = {
          navController.navigate(Screen.Verify.route)
        }
      )
    }
  }

  if (state.rotationState == BackupKeyRotationState.GENERATING_KEY || state.rotationState == BackupKeyRotationState.COMMITTING_KEY) {
    Dialogs.IndeterminateProgressDialog()
  }

  if (state.rotationState == BackupKeyRotationState.NOT_ALLOWED) {
    val onAcknowledged = {
      if (startWithKeyRotation) {
        currentOnClose()
      } else {
        viewModel.onRotationRefusalAcknowledged()
      }
    }

    if (!state.canRotateKey) {
      KeyLimitExceededDialog(
        areBackupsEnabled = state.areBackupsEnabled,
        onClick = { onAcknowledged() }
      )
    } else {
      DownloadMediaDialog(
        onTurnOffAndDownloadClick = {
          viewModel.turnOffOptimizedStorageAndDownloadMedia()
          currentOnClose()
        },
        onCancelClick = { onAcknowledged() }
      )
    }
  }

  if (displayWarningDialog) {
    BackupKeyNotCommitedWarningDialog(
      onConfirm = {
        currentOnClose()
      },
      onCancel = {
        displayWarningDialog = false
        navController.navigate(Screen.Verify.route)
      }
    )
  }

  Nav.Host(
    navController = navController,
    startDestination = Screen.Record.route
  ) {
    composable(Screen.Record.route) {
      MessageBackupsKeyRecordScreen(
        backupKey = state.accountEntropyPool.displayValue,
        keySaveState = state.keySaveState,
        backupKeyCredentialManagerHandler = viewModel,
        mode = mode
      )
    }

    composable(Screen.Verify.route) {
      MessageBackupsKeyVerifyScreen(
        backupKey = state.accountEntropyPool.displayValue,
        onNavigationClick = { onBackPressedDispatcher?.onBackPressed() },
        onNextClick = { viewModel.commitBackupKey() }
      )
    }
  }
}

@Composable
private fun BackupKeyNotCommitedWarningDialog(
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.BackupKeyDisplayFragment__cancel_key_creation_question),
    body = stringResource(R.string.BackupKeyDisplayFragment__your_new_backup_key),
    confirm = stringResource(R.string.BackupKeyDisplayFragment__cancel_key_creation),
    dismiss = stringResource(R.string.BackupKeyDisplayFragment__confirm_key),
    onConfirm = onConfirm,
    onDeny = onCancel
  )
}

private enum class Screen(val route: String) {
  Record("record-screen"),
  Verify("verify-screen")
}

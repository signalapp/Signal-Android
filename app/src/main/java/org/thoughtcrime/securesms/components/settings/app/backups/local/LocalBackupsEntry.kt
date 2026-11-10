/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Launchers
import org.signal.core.ui.compose.requireFragmentActivity
import org.signal.core.ui.util.StorageUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyEducationScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyEducationScreenMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Environment
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether [route] opens the on-device backups screen rather than the legacy backups preferences, which remain for
 * users still on the old local backup format.
 */
fun AppSettingsRoute.BackupsRoute.Local.usesOnDeviceBackupsScreen(): Boolean {
  return SignalStore.account.isLinkedDevice ||
    SignalStore.backup.newLocalBackupsEnabled ||
    (Environment.Backups.isNewFormatSupportedForLocalBackup() && (!SignalStore.settings.isBackupEnabled || triggerUpdateFlow))
}

/**
 * On-device backups settings, including the flow that upgrades a legacy backup to the new format and records its
 * recovery key. That flow keeps its own back stack, and leaving its first screen leaves this entry.
 *
 * @param triggerUpdateFlow True to start in the upgrade flow instead of the settings.
 */
@Composable
fun LocalBackupsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  triggerUpdateFlow: Boolean = false
) {
  val activity = requireFragmentActivity()
  val initialKey = if (triggerUpdateFlow) LocalBackupsNavKey.IMPROVEMENTS else LocalBackupsNavKey.SETTINGS
  val backstack = rememberNavBackStack(initialKey)
  val snackbarHostState = remember { SnackbarHostState() }
  val viewModel = viewModel<LocalBackupsViewModel>()

  val navigateBack: () -> Unit = {
    if (backstack.size > 1) {
      backstack.removeLastOrNull()
    } else {
      parentEventEmitter.navigateBack()
    }
  }

  NavDisplay(
    backStack = backstack,
    entryProvider = { key ->
      when (key) {
        LocalBackupsNavKey.SETTINGS -> NavEntry(key) {
          val chooseBackupLocationLauncher = rememberChooseBackupLocationLauncher(backstack, viewModel)
          val state by viewModel.settingsState.collectAsStateWithLifecycle()
          val callback: LocalBackupsSettingsCallback = remember(activity, chooseBackupLocationLauncher, viewModel, parentEventEmitter) {
            DefaultLocalBackupsSettingsCallback(
              activity = activity,
              chooseBackupLocationLauncher = chooseBackupLocationLauncher,
              viewModel = viewModel,
              parentEventEmitter = parentEventEmitter
            )
          }

          LifecycleResumeEffect(Unit) {
            viewModel.refreshSettingsState()
            onPauseOrDispose {}
          }

          LocalBackupsSettingsScreen(
            state = state,
            callback = callback,
            snackbarHostState = snackbarHostState
          )
        }

        LocalBackupsNavKey.IMPROVEMENTS -> NavEntry(key) {
          LocalBackupsImprovementsScreen(
            onNavigationClick = navigateBack,
            onContinueClick = { backstack.add(LocalBackupsNavKey.YOUR_RECOVERY_KEY) }
          )
        }

        LocalBackupsNavKey.YOUR_RECOVERY_KEY -> NavEntry(key) {
          MessageBackupsKeyEducationScreen(
            onNavigationClick = navigateBack,
            onNextClick = { backstack.add(LocalBackupsNavKey.RECORD_RECOVERY_KEY) },
            mode = if (triggerUpdateFlow) {
              MessageBackupsKeyEducationScreenMode.LOCAL_BACKUP_UPGRADE
            } else if (SignalStore.backup.areBackupsEnabled) {
              MessageBackupsKeyEducationScreenMode.LOCAL_WITH_REMOTE_ENABLED
            } else {
              MessageBackupsKeyEducationScreenMode.DEFAULT
            }
          )
        }

        LocalBackupsNavKey.RECORD_RECOVERY_KEY -> NavEntry(key) {
          val state: LocalBackupsKeyState by viewModel.backupState.collectAsStateWithLifecycle()

          MessageBackupsKeyRecordScreen(
            backupKey = state.accountEntropyPool.displayValue,
            keySaveState = state.keySaveState,
            backupKeyCredentialManagerHandler = viewModel,
            mode = MessageBackupsKeyRecordMode.Next {
              backstack.add(LocalBackupsNavKey.CONFIRM_RECOVERY_KEY)
            }
          )
        }

        LocalBackupsNavKey.CONFIRM_RECOVERY_KEY -> NavEntry(key) {
          val state: LocalBackupsKeyState by viewModel.backupState.collectAsStateWithLifecycle()
          val scope = rememberCoroutineScope()
          val backupKeyUpdatedMessage = stringResource(R.string.OnDeviceBackupsFragment__recovery_key_updated)
          var upgradeInProgress by remember { mutableStateOf(false) }

          MessageBackupsKeyVerifyScreen(
            backupKey = state.accountEntropyPool.displayValue,
            onNavigationClick = navigateBack,
            onNextClick = {
              scope.launch {
                upgradeInProgress = true
                viewModel.handleUpgrade(activity)
                upgradeInProgress = false

                if (!backstack.contains(LocalBackupsNavKey.SETTINGS)) {
                  backstack.add(0, LocalBackupsNavKey.SETTINGS)
                }

                backstack.removeAll { it != LocalBackupsNavKey.SETTINGS }

                snackbarHostState.showSnackbar(
                  message = backupKeyUpdatedMessage
                )
              }
            }
          )

          Dialogs.IndeterminateProgressDialog(
            visible = upgradeInProgress,
            delayDuration = 100.milliseconds,
            minimumDisplayDuration = 500.milliseconds
          )
        }

        else -> error("Unknown key: $key")
      }
    }
  )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun rememberChooseBackupLocationLauncher(backStack: NavBackStack<NavKey>, viewModel: LocalBackupsViewModel): ActivityResultLauncher<Uri?> {
  val context = LocalContext.current
  return Launchers.rememberOpenDocumentTreeLauncher { uri ->
    viewModel.onBackupDirectorySelected(context, uri)
    if (uri != null) {
      backStack.add(LocalBackupsNavKey.YOUR_RECOVERY_KEY)
      Toast.makeText(context, context.getString(R.string.OnDeviceBackupsFragment__directory_selected, StorageUtil.getDisplayPath(context, uri)), Toast.LENGTH_SHORT).show()
    }
  }
}

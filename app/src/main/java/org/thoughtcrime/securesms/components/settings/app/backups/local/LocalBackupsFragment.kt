/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.navArgs
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyEducationScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyEducationScreenMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore

private val TAG = Log.tag(LocalBackupsFragment::class)

/**
 * On-device backups settings screen, replaces `BackupsPreferenceFragment` and contains the key upgrade flow.
 */
class LocalBackupsFragment : ComposeFragment() {

  private val args: LocalBackupsFragmentArgs by navArgs()

  @Composable
  override fun FragmentContent() {
    val initialStack = if (args.triggerUpdateFlow) {
      arrayOf(LocalBackupsNavKey.IMPROVEMENTS)
    } else {
      arrayOf(LocalBackupsNavKey.SETTINGS)
    }

    val backstack = rememberNavBackStack(*initialStack)
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel = viewModel<LocalBackupsViewModel>()

    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides requireActivity()) {
      NavDisplay(
        backStack = backstack,
        entryProvider = { key ->
          when (key) {
            LocalBackupsNavKey.SETTINGS -> NavEntry(key) {
              val chooseBackupLocationLauncher = rememberChooseBackupLocationLauncher(backstack)
              val state by viewModel.settingsState.collectAsStateWithLifecycle()
              val callback: LocalBackupsSettingsCallback = remember(
                chooseBackupLocationLauncher
              ) {
                DefaultLocalBackupsSettingsCallback(
                  fragment = this,
                  chooseBackupLocationLauncher = chooseBackupLocationLauncher,
                  viewModel = viewModel
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
              val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current

              LocalBackupsImprovementsScreen(
                onNavigationClick = { backPressedDispatcher?.onBackPressedDispatcher?.onBackPressed() },
                onContinueClick = { backstack.add(LocalBackupsNavKey.YOUR_RECOVERY_KEY) }
              )
            }

            LocalBackupsNavKey.YOUR_RECOVERY_KEY -> NavEntry(key) {
              val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current

              MessageBackupsKeyEducationScreen(
                onNavigationClick = { backPressedDispatcher?.onBackPressedDispatcher?.onBackPressed() },
                onNextClick = { backstack.add(LocalBackupsNavKey.RECORD_RECOVERY_KEY) },
                mode = MessageBackupsKeyEducationScreenMode.LOCAL_BACKUP_UPGRADE
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
              val backupKeyUpdatedMessage = stringResource(R.string.OnDeviceBackupsFragment__backup_key_updated)

              MessageBackupsKeyVerifyScreen(
                backupKey = state.accountEntropyPool.displayValue,
                onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                onNextClick = {
                  if (!backstack.contains(LocalBackupsNavKey.SETTINGS)) {
                    backstack.add(0, LocalBackupsNavKey.SETTINGS)
                  }

                  backstack.removeAll { it != LocalBackupsNavKey.SETTINGS }

                  scope.launch {
                    viewModel.handleUpgrade(requireContext())

                    snackbarHostState.showSnackbar(
                      message = backupKeyUpdatedMessage
                    )
                  }
                }
              )
            }

            else -> error("Unknown key: $key")
          }
        }
      )
    }
  }
}

@Composable
private fun rememberChooseBackupLocationLauncher(backStack: NavBackStack<NavKey>): ActivityResultLauncher<Intent> {
  val context = LocalContext.current
  return rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val uri = result.data?.data
    if (result.resultCode == Activity.RESULT_OK && uri != null) {
      Log.i(TAG, "Backup location selected: $uri")
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(uri, takeFlags)
      SignalStore.backup.newLocalBackupsDirectory = uri.toString()
      backStack.add(LocalBackupsNavKey.YOUR_RECOVERY_KEY)

      Toast.makeText(context, context.getString(R.string.OnDeviceBackupsFragment__directory_selected, uri), Toast.LENGTH_SHORT).show()
    } else {
      Log.w(TAG, "Unified backup location selection cancelled or failed")
    }
  }
}

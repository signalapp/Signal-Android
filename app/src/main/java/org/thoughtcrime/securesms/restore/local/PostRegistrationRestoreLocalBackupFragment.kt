/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.local

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.restore.EnterBackupKeyViewModel
import org.thoughtcrime.securesms.registration.ui.restore.local.RestoreLocalBackupActivity
import org.thoughtcrime.securesms.registration.ui.restore.local.RestoreLocalBackupCallback
import org.thoughtcrime.securesms.registration.ui.restore.local.RestoreLocalBackupNavDisplay
import org.thoughtcrime.securesms.registration.ui.restore.local.RestoreLocalBackupViewModel
import org.thoughtcrime.securesms.registration.ui.restore.local.SelectableBackup
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Post Registration restore fragment for V2 backups.
 */
class PostRegistrationRestoreLocalBackupFragment : ComposeFragment() {

  companion object {
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val sharedViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel by viewModels<RestoreLocalBackupViewModel>()
  private val enterBackupKeyViewModel by viewModels<EnterBackupKeyViewModel>()

  @Composable
  override fun FragmentContent() {
    val state by restoreLocalBackupViewModel.state.collectAsStateWithLifecycle()
    val enterBackupKeyState by enterBackupKeyViewModel.state.collectAsStateWithLifecycle()

    SignalTheme {
      val activity = LocalActivity.current as FragmentActivity
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides activity) {
        RestoreLocalBackupNavDisplay(
          state = state,
          callback = remember { Callbacks() },
          isRegistrationInProgress = false,
          enterBackupKeyState = enterBackupKeyState,
          backupKey = enterBackupKeyViewModel.backupKey
        )
      }
    }
  }

  private inner class Callbacks : RestoreLocalBackupCallback {
    override fun setSelectedBackup(backup: SelectableBackup) {
      restoreLocalBackupViewModel.setSelectedBackup(backup)
    }

    override fun setSelectedBackupDirectory(context: Context, uri: Uri): Boolean {
      return restoreLocalBackupViewModel.setSelectedBackupDirectory(context, uri)
    }

    override fun displaySkipRestoreWarning() {
      restoreLocalBackupViewModel.displaySkipRestoreWarning()
    }

    override fun clearDialog() {
      restoreLocalBackupViewModel.clearDialog()
    }

    override fun skipRestore() {
      sharedViewModel.skipRestore()

      viewLifecycleOwner.lifecycleScope.launch {
        sharedViewModel.performStorageServiceAccountRestoreIfNeeded()

        withContext(Dispatchers.Main) {
          startActivity(MainActivity.clearTop(requireContext()))
          activity?.finish()
        }
      }
    }

    override fun submitBackupKey() {
      val aep = AccountEntropyPool.parseOrNull(enterBackupKeyViewModel.backupKey) ?: return
      SignalStore.account.restoreAccountEntropyPool(aep)

      val selectedTimestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: -1L
      SignalStore.backup.newLocalBackupsSelectedSnapshotTimestamp = selectedTimestamp

      startActivity(RestoreLocalBackupActivity.getIntent(requireContext()))
      requireActivity().supportFinishAfterTransition()
    }

    override fun routeToLegacyBackupRestoration(uri: Uri) {
      sharedViewModel.setBackupFileUri(uri)
      findNavController().safeNavigate(PostRegistrationRestoreLocalBackupFragmentDirections.restoreLocalV1Backup())
    }

    override fun onBackupKeyChanged(key: String) {
      enterBackupKeyViewModel.updateBackupKey(key)
      val timestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: return
      enterBackupKeyViewModel.verifyLocalBackupKey(timestamp)
    }

    override fun clearRegistrationError() {
      enterBackupKeyViewModel.clearRegistrationError()
    }

    override fun onBackupKeyHelp() {
      CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL)
    }
  }
}

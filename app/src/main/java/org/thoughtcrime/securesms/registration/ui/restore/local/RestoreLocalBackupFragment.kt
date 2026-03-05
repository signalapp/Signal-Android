/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.registration.ui.restore.EnterBackupKeyViewModel
import org.thoughtcrime.securesms.restore.RestoreActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Restore an on-device backup during registration
 */
class RestoreLocalBackupFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class)
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val enterBackupKeyViewModel by viewModels<EnterBackupKeyViewModel>()
  private lateinit var restoreLocalBackupViewModel: RestoreLocalBackupViewModel

  private val localBackupRestore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
    when (val resultCode = result.resultCode) {
      Activity.RESULT_OK -> {
        sharedViewModel.onBackupSuccessfullyRestored()
        findNavController().safeNavigate(RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
      }

      Activity.RESULT_CANCELED -> {
        Log.w(TAG, "Backup restoration canceled.")
      }

      else -> Log.w(TAG, "Backup restoration activity ended with unknown result code: $resultCode")
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            enterBackupKeyViewModel.handleRegistrationFailure(it)
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val viewModel = viewModel<RestoreLocalBackupViewModel>()
    restoreLocalBackupViewModel = viewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registrationState by sharedViewModel.state.collectAsStateWithLifecycle()
    val enterBackupKeyState by enterBackupKeyViewModel.state.collectAsStateWithLifecycle()

    SignalTheme {
      val activity = LocalActivity.current as FragmentActivity
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides activity) {
        RestoreLocalBackupNavDisplay(
          state = state,
          callback = remember { RestoreBackupCallback() },
          isRegistrationInProgress = registrationState.inProgress,
          enterBackupKeyState = enterBackupKeyState,
          backupKey = enterBackupKeyViewModel.backupKey
        )
      }
    }
  }

  private inner class RestoreBackupCallback : RestoreLocalBackupCallback {
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
      findNavController().safeNavigate(RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
    }

    override fun routeToLegacyBackupRestoration(uri: Uri) {
      sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = false, fromLocalV2 = false)
      localBackupRestore.launch(RestoreActivity.getLocalRestoreIntent(requireContext(), uri))
    }

    override fun submitBackupKey() {
      enterBackupKeyViewModel.registering()

      val selectedTimestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: -1L
      SignalStore.backup.newLocalBackupsSelectedSnapshotTimestamp = selectedTimestamp

      sharedViewModel.registerWithBackupKey(
        context = requireContext(),
        backupKey = enterBackupKeyViewModel.backupKey,
        e164 = null,
        pin = null,
        aciIdentityKeyPair = null,
        pniIdentityKeyPair = null
      )
    }

    override fun onBackupKeyChanged(key: String) {
      enterBackupKeyViewModel.updateBackupKey(key)
    }

    override fun clearRegistrationError() {
      enterBackupKeyViewModel.clearRegistrationError()
    }

    override fun onBackupKeyHelp() {
      CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL)
    }
  }
}

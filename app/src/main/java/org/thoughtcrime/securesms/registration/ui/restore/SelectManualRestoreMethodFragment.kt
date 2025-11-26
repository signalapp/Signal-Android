/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Dialogs
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.restore.RestoreActivity
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Provide options to select restore/transfer operation and flow during manual registration.
 */
class SelectManualRestoreMethodFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(SelectManualRestoreMethodFragment::class)
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()

  private val localBackupRestore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
    when (val resultCode = result.resultCode) {
      Activity.RESULT_OK -> {
        sharedViewModel.onBackupSuccessfullyRestored()
        findNavController().safeNavigate(SelectManualRestoreMethodFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.NORMAL))
      }
      Activity.RESULT_CANCELED -> {
        Log.w(TAG, "Backup restoration canceled.")
      }
      else -> Log.w(TAG, "Backup restoration activity ended with unknown result code: $resultCode")
    }
  }

  @Composable
  override fun FragmentContent() {
    var showSkipRestoreWarning by remember { mutableStateOf(false) }

    SelectRestoreMethodScreen(
      restoreMethods = listOf(RestoreMethod.FROM_SIGNAL_BACKUPS, RestoreMethod.FROM_LOCAL_BACKUP_V1),
      onRestoreMethodClicked = this::startRestoreMethod,
      onSkip = {
        showSkipRestoreWarning = true
      }
    ) {
      if (showSkipRestoreWarning) {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_title),
          body = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_warning),
          confirm = stringResource(R.string.SelectRestoreMethodFragment__skip_restore),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = {
            sharedViewModel.skipRestore()
            findNavController().safeNavigate(SelectManualRestoreMethodFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.NORMAL))
          },
          onDismiss = { showSkipRestoreWarning = false },
          confirmColor = MaterialTheme.colorScheme.error,
          properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
      }
    }
  }

  private fun startRestoreMethod(method: RestoreMethod) {
    when (method) {
      RestoreMethod.FROM_SIGNAL_BACKUPS -> {
        sharedViewModel.clearPreviousRegistrationState()
        sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
        findNavController().safeNavigate(SelectManualRestoreMethodFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.COLLECT_FOR_MANUAL_SIGNAL_BACKUPS_RESTORE))
      }
      RestoreMethod.FROM_LOCAL_BACKUP_V1 -> {
        sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = false)
        localBackupRestore.launch(RestoreActivity.getLocalRestoreIntent(requireContext()))
      }
      RestoreMethod.FROM_OLD_DEVICE -> error("Device transfer not supported in manual restore flow")
      RestoreMethod.FROM_LOCAL_BACKUP_V2 -> error("Not currently supported")
    }
  }
}

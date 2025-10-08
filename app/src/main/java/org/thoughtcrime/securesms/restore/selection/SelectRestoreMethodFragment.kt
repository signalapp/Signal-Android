/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.selection

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registration.ui.restore.RemoteRestoreActivity
import org.thoughtcrime.securesms.registration.ui.restore.RestoreMethod
import org.thoughtcrime.securesms.registration.ui.restore.SelectRestoreMethodScreen
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.provisioning.RestoreMethod as ApiRestoreMethod

/**
 * Provide options to select restore/transfer operation during quick/post registration.
 */
class SelectRestoreMethodFragment : ComposeFragment() {

  private val viewModel: RestoreViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    var showSkipRestoreWarning by remember { mutableStateOf(false) }

    SelectRestoreMethodScreen(
      restoreMethods = viewModel.getAvailableRestoreMethods(),
      onRestoreMethodClicked = this::startRestoreMethod,
      onSkip = { showSkipRestoreWarning = true }
    ) {
      if (viewModel.showStorageAccountRestoreProgress) {
        Dialogs.IndeterminateProgressDialog()
      } else if (showSkipRestoreWarning) {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_title),
          body = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_warning),
          confirm = stringResource(R.string.SelectRestoreMethodFragment__skip_restore),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = {
            lifecycleScope.launch {
              viewModel.skipRestore()
              viewModel.performStorageServiceAccountRestoreIfNeeded()

              if (isActive) {
                withContext(Dispatchers.Main) {
                  startActivity(MainActivity.clearTop(requireContext()))
                  activity?.finish()
                }
              }
            }
          },
          onDismiss = { showSkipRestoreWarning = false },
          confirmColor = MaterialTheme.colorScheme.error
        )
      }
    }
  }

  private fun startRestoreMethod(method: RestoreMethod) {
    val apiRestoreMethod = when (method) {
      RestoreMethod.FROM_SIGNAL_BACKUPS -> ApiRestoreMethod.REMOTE_BACKUP
      RestoreMethod.FROM_LOCAL_BACKUP_V1, RestoreMethod.FROM_LOCAL_BACKUP_V2 -> ApiRestoreMethod.LOCAL_BACKUP
      RestoreMethod.FROM_OLD_DEVICE -> ApiRestoreMethod.DEVICE_TRANSFER
    }

    lifecycleScope.launch {
      QuickRegistrationRepository.setRestoreMethodForOldDevice(apiRestoreMethod)
    }

    when (method) {
      RestoreMethod.FROM_SIGNAL_BACKUPS -> {
        if (viewModel.hasRestoredBackupDataFromQr()) {
          startActivity(RemoteRestoreActivity.getIntent(requireContext()))
        } else {
          findNavController().safeNavigate(SelectRestoreMethodFragmentDirections.goToPostRestoreEnterBackupKey())
        }
      }
      RestoreMethod.FROM_OLD_DEVICE -> findNavController().safeNavigate(SelectRestoreMethodFragmentDirections.goToDeviceTransfer())
      RestoreMethod.FROM_LOCAL_BACKUP_V1 -> findNavController().safeNavigate(SelectRestoreMethodFragmentDirections.goToLocalBackupRestore())
      RestoreMethod.FROM_LOCAL_BACKUP_V2 -> error("Not currently supported")
    }
  }
}

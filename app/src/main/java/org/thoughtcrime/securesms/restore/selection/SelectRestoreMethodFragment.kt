/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.selection

import androidx.compose.runtime.Composable
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.Dialogs
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registrationv3.ui.restore.RemoteRestoreActivity
import org.thoughtcrime.securesms.registrationv3.ui.restore.RestoreMethod
import org.thoughtcrime.securesms.registrationv3.ui.restore.SelectRestoreMethodScreen
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.registration.RestoreMethod as ApiRestoreMethod

/**
 * Provide options to select restore/transfer operation during quick/post registration.
 */
class SelectRestoreMethodFragment : ComposeFragment() {

  private val viewModel: RestoreViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    SelectRestoreMethodScreen(
      restoreMethods = viewModel.getAvailableRestoreMethods(),
      onRestoreMethodClicked = this::startRestoreMethod,
      onSkip = {
        viewLifecycleOwner.lifecycleScope.launch {
          viewModel.skipRestore()
          viewModel.performStorageServiceAccountRestoreIfNeeded()

          if (isActive) {
            withContext(Dispatchers.Main) {
              startActivity(MainActivity.clearTop(requireContext()))
              activity?.finish()
            }
          }
        }
      }
    ) {
      if (viewModel.showStorageAccountRestoreProgress) {
        Dialogs.IndeterminateProgressDialog()
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
        if (viewModel.hasRestoredAccountEntropyPool()) {
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

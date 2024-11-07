/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.selection

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registrationv3.ui.restore.RemoteRestoreActivity
import org.thoughtcrime.securesms.registrationv3.ui.restore.RestoreMethod
import org.thoughtcrime.securesms.registrationv3.ui.restore.SelectRestoreMethodScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Provide options to select restore/transfer operation and flow during quick registration.
 */
class SelectRestoreMethodFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    SelectRestoreMethodScreen(
      restoreMethods = listOf(RestoreMethod.FROM_SIGNAL_BACKUPS, RestoreMethod.FROM_OLD_DEVICE, RestoreMethod.FROM_LOCAL_BACKUP_V1), // TODO [backups] make dynamic
      onRestoreMethodClicked = this::startRestoreMethod,
      onSkip = {
        SignalStore.registration.markSkippedTransferOrRestore()
        startActivity(MainActivity.clearTop(requireContext()))
        activity?.finish()
      }
    )
  }

  private fun startRestoreMethod(method: RestoreMethod) {
    when (method) {
      RestoreMethod.FROM_SIGNAL_BACKUPS -> startActivity(Intent(requireContext(), RemoteRestoreActivity::class.java))
      RestoreMethod.FROM_OLD_DEVICE -> findNavController().safeNavigate(SelectRestoreMethodFragmentDirections.goToDeviceTransfer())
      RestoreMethod.FROM_LOCAL_BACKUP_V1 -> findNavController().safeNavigate(SelectRestoreMethodFragmentDirections.goToLocalBackupRestore())
      RestoreMethod.FROM_LOCAL_BACKUP_V2 -> error("Not currently supported")
    }
  }
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Delegate that controls whether and which backup alert sheet is displayed.
 */
object BackupAlertDelegate {
  @JvmStatic
  fun delegate(fragmentManager: FragmentManager, lifecycle: Lifecycle) {
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (BackupRepository.shouldDisplayBackupFailedSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = SignalStore.backup.daysSinceLastBackup)).show(fragmentManager, null)
        }

        // TODO [backups]
        // Get unnotified backup download failures & display sheet
      }
    }
  }
}

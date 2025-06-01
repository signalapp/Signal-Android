/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
          BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(fragmentManager, null)
        } else if (BackupRepository.shouldDisplayCouldNotCompleteBackupSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = SignalStore.backup.daysSinceLastBackup)).show(fragmentManager, null)
        } else if (withContext(Dispatchers.IO) { BackupRepository.shouldDisplayYourMediaWillBeDeletedTodaySheet() }) {
          BackupAlertBottomSheet.create(BackupAlert.MediaWillBeDeletedToday).show(fragmentManager, null)
        } else if (BackupRepository.shouldDisplayBackupExpiredAndDowngradedSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.ExpiredAndDowngraded).show(fragmentManager, null)
        }
      }
    }
  }
}

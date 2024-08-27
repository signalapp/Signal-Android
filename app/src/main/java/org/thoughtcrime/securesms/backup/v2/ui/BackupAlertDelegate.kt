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

/**
 * Delegate that controls whether and which backup alert sheet is displayed.
 */
object BackupAlertDelegate {
  @JvmStatic
  fun delegate(fragmentManager: FragmentManager, lifecycle: Lifecycle) {
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        // TODO [message-backups]
        // 1. Get unnotified backup upload failures
        // 2. Get unnotified backup download failures
        // 3. Get unnotified backup payment failures

        // Decide which do display
      }
    }
  }
}

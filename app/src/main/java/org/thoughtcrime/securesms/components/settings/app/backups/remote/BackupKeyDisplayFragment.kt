/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util

/**
 * Fragment which only displays the backup key to the user.
 */
class BackupKeyDisplayFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    MessageBackupsKeyRecordScreen(
      backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey(),
      onNavigationClick = { findNavController().popBackStack() },
      onCopyToClipboardClick = { Util.copyToClipboard(requireContext(), it) },
      onNextClick = { findNavController().popBackStack() }
    )
  }
}

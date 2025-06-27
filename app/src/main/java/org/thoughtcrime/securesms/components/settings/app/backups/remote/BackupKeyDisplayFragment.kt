/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment which only displays the backup key to the user.
 */
class BackupKeyDisplayFragment : ComposeFragment() {

  companion object {
    const val CLIPBOARD_TIMEOUT_SECONDS = 60
  }

  private val viewModel: BackupKeyDisplayViewModel by viewModel { BackupKeyDisplayViewModel() }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MessageBackupsKeyRecordScreen(
      backupKey = SignalStore.account.accountEntropyPool.displayValue,
      keySaveState = state.keySaveState,
      onNavigationClick = { findNavController().popBackStack() },
      onCopyToClipboardClick = { Util.copyToClipboard(requireContext(), it, CLIPBOARD_TIMEOUT_SECONDS) },
      onRequestSaveToPasswordManager = viewModel::onBackupKeySaveRequested,
      onConfirmSaveToPasswordManager = viewModel::onBackupKeySaveConfirmed,
      onSaveToPasswordManagerComplete = viewModel::onBackupKeySaveCompleted,
      onNextClick = { findNavController().popBackStack() },
      onGoToDeviceSettingsClick = {
        val intent = BackupKeyCredentialManagerHandler.getCredentialManagerSettingsIntent(requireContext())
        requireContext().startActivity(intent)
      }
    )
  }
}

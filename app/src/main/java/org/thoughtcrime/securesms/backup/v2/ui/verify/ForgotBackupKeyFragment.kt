package org.thoughtcrime.securesms.backup.v2.ui.verify

import android.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.storage.AndroidCredentialRepository
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment which displays the backup key to the user after users forget it.
 */
class ForgotBackupKeyFragment : ComposeFragment() {

  companion object {
    const val CLIPBOARD_TIMEOUT_SECONDS = 60
  }

  private val viewModel: ForgotBackupKeyViewModel by viewModel { ForgotBackupKeyViewModel() }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordManagerSettingsIntent = AndroidCredentialRepository.getCredentialManagerSettingsIntent(requireContext())

    MessageBackupsKeyRecordScreen(
      backupKey = SignalStore.account.accountEntropyPool.displayValue,
      keySaveState = state.keySaveState,
      canOpenPasswordManagerSettings = passwordManagerSettingsIntent != null,
      onNavigationClick = { requireActivity().supportFragmentManager.popBackStack() },
      onCopyToClipboardClick = { Util.copyToClipboard(requireContext(), it, CLIPBOARD_TIMEOUT_SECONDS) },
      onRequestSaveToPasswordManager = viewModel::onBackupKeySaveRequested,
      onConfirmSaveToPasswordManager = viewModel::onBackupKeySaveConfirmed,
      onSaveToPasswordManagerComplete = viewModel::onBackupKeySaveCompleted,
      mode = remember {
        MessageBackupsKeyRecordMode.Next(onNextClick = {
          requireActivity()
            .supportFragmentManager
            .beginTransaction()
            .add(R.id.content, ConfirmBackupKeyDisplayFragment())
            .addToBackStack(null)
            .commit()
        })
      },
      onGoToPasswordManagerSettingsClick = { requireContext().startActivity(passwordManagerSettingsIntent) }
    )
  }
}

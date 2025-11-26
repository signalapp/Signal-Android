package org.thoughtcrime.securesms.backup.v2.ui.verify

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyCredentialManagerHandler
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeySaveState

/**
 * View model for [ForgotBackupKeyFragment]
 */
class ForgotBackupKeyViewModel : ViewModel(), BackupKeyCredentialManagerHandler {
  private val internalUiState = MutableStateFlow(BackupKeyDisplayUiState())
  val uiState: StateFlow<BackupKeyDisplayUiState> = internalUiState

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    internalUiState.update { it.copy(keySaveState = newState) }
  }
}

data class BackupKeyDisplayUiState(
  val keySaveState: BackupKeySaveState? = null
)

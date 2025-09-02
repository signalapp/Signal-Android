/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.milliseconds

class BackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(BackupsSettingsViewModel::class)
  }

  private val internalStateFlow: MutableStateFlow<BackupsSettingsState>

  val stateFlow: StateFlow<BackupsSettingsState> by lazy { internalStateFlow }

  init {
    val repo = BackupStateObserver(viewModelScope, useDatabaseFallbackOnNetworkError = true)
    internalStateFlow = MutableStateFlow(BackupsSettingsState(backupState = repo.backupState.value))

    viewModelScope.launch {
      repo.backupState.collect { enabledState ->
        Log.d(TAG, "Found enabled state $enabledState. Updating UI state.")
        internalStateFlow.update {
          it.copy(
            backupState = enabledState,
            lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
            showBackupTierInternalOverride = RemoteConfig.internalUser || Environment.IS_STAGING,
            backupTierInternalOverride = SignalStore.backup.backupTierInternalOverride
          )
        }
      }
    }
  }

  fun onBackupTierInternalOverrideChanged(tier: MessageBackupTier?) {
    SignalStore.backup.backupTierInternalOverride = tier
    SignalStore.backup.deletionState = DeletionState.NONE
    viewModelScope.launch(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    BackupStateObserver.notifyBackupTierChanged(scope = viewModelScope)
  }
}

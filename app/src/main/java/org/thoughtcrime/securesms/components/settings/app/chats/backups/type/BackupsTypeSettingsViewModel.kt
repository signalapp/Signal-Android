/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore

class BackupsTypeSettingsViewModel : ViewModel() {
  private val internalState = MutableStateFlow(BackupsTypeSettingsState())

  val state: StateFlow<BackupsTypeSettingsState> = internalState

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      val tier = SignalStore.backup.backupTier
      val paymentMethod = withContext(Dispatchers.IO) {
        InAppPaymentsRepository.getLatestPaymentMethodType(InAppPaymentSubscriberRecord.Type.BACKUP)
      }

      internalState.update {
        it.copy(
          messageBackupsType = if (tier != null) BackupRepository.getBackupsType(tier) else null,
          paymentSourceType = paymentMethod.toPaymentSourceType()
        )
      }
    }
  }
}

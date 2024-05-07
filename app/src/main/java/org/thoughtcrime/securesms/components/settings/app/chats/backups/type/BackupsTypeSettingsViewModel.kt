/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups.type

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore

class BackupsTypeSettingsViewModel : ViewModel() {
  private val internalState = mutableStateOf(
    BackupsTypeSettingsState(
      backupsTier = SignalStore.backup().backupTier
    )
  )

  val state: State<BackupsTypeSettingsState> = internalState
}

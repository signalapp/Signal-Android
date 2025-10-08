/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.thoughtcrime.securesms.keyvalue.SignalStore

class NewConversationViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(NewConversationUiState())
  val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()
}

data class NewConversationUiState(
  val forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPaneOnCompactLandscape
)

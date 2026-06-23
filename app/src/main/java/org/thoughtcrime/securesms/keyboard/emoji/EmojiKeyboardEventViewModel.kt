/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard.emoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Glue ViewModel that allows a component to dispatch emoji events to subcomponents.
 */
class EmojiKeyboardEventViewModel : ViewModel() {

  private val eventChannel = Channel<EmojiKeyboardEvent>(Channel.BUFFERED)
  val events: Flow<EmojiKeyboardEvent> = eventChannel.receiveAsFlow()

  fun onEvent(event: EmojiKeyboardEvent) {
    viewModelScope.launch {
      eventChannel.send(event)
    }
  }
}

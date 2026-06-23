/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.signal.core.util.BreakIteratorCompat

class AddMessageViewModel(initialMessage: CharSequence?) : ViewModel() {

  var message: CharSequence? = initialMessage
  var isViewOnce: Boolean = false

  private val addAMessageUpdatePublisher = Channel<CharSequence>(Channel.CONFLATED)

  fun watchAddAMessageCount(): Flow<AddMessageCharacterCount> {
    return addAMessageUpdatePublisher
      .receiveAsFlow()
      .map {
        val iterator = BreakIteratorCompat.getInstance()
        iterator.setText(it)
        AddMessageCharacterCount(iterator.countBreaks())
      }
      .flowOn(Dispatchers.IO)
  }

  fun updateAddAMessageCount(input: CharSequence?) {
    viewModelScope.launch {
      addAMessageUpdatePublisher.send(input ?: "")
    }
  }
}

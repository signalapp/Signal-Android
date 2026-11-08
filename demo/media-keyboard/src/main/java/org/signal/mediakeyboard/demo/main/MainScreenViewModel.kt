/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.logging.Log

class MainScreenViewModel : EventDrivenViewModel<MainScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(MainScreenViewModel::class)
  }

  private val _state = MutableStateFlow(MainScreenState())
  val state: StateFlow<MainScreenState> = _state.asStateFlow()

  override suspend fun processEvent(event: MainScreenEvents) {
    val state = _state.value

    _state.value = when (event) {
      MainScreenEvents.OpenMediaKeyboard -> state.copy(keyboardVisible = true)

      MainScreenEvents.MediaKeyboardDismissed -> state.copy(keyboardVisible = false)

      is MainScreenEvents.ComposerTextChanged -> state.copy(composerText = event.text)

      MainScreenEvents.SendClicked -> {
        if (state.composerText.isBlank()) {
          state
        } else {
          state.copy(
            messages = state.messages + DemoMessage.Text(state.composerText.trim()),
            composerText = ""
          )
        }
      }

      is MainScreenEvents.EmojiSelected -> state.copy(composerText = state.composerText + event.emoji)

      MainScreenEvents.BackspacePressed -> state.copy(composerText = state.composerText.dropLastGrapheme())

      is MainScreenEvents.StickerSelected -> state.copy(messages = state.messages + DemoMessage.Sticker(event.sticker))

      is MainScreenEvents.GifSelected -> {
        state.copy(
          messages = state.messages + DemoMessage.Gif(event.gif),
          keyboardVisible = false
        )
      }
    }
  }

  private fun String.dropLastGrapheme(): String {
    if (isEmpty()) {
      return this
    }

    val iterator = BreakIteratorCompat.getInstance()
    iterator.setText(this)

    return iterator.take(iterator.countBreaks() - 1).toString()
  }
}

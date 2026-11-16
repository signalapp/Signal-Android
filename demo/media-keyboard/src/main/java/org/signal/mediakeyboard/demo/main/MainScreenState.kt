/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.main

import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.data.KeyboardSticker
import java.util.concurrent.atomic.AtomicLong

data class MainScreenState(
  val messages: List<DemoMessage> = emptyList(),
  val composerText: String = "",
  val keyboardVisible: Boolean = false
)

sealed class DemoMessage {
  val id: Long = NEXT_ID.getAndIncrement()

  data class Text(val text: String) : DemoMessage()
  data class Sticker(val sticker: KeyboardSticker) : DemoMessage()
  data class Gif(val gif: KeyboardGif) : DemoMessage()

  private companion object {
    val NEXT_ID = AtomicLong()
  }
}

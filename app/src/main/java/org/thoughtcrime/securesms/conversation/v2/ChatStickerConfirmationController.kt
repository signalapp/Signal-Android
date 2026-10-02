/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.signal.mediakeyboard.data.KeyboardSticker

/**
 * Holds the sticker waiting on the user to confirm it.
 *
 * @param onShowingChanged Called as a confirmation appears or goes away, for the host to get the
 *   input panel out of the way while one shows.
 */
class ChatStickerConfirmationController(
  private val onSend: (KeyboardSticker) -> Unit,
  private val onShowingChanged: (Boolean) -> Unit
) {

  var confirmation: StickerConfirmation? by mutableStateOf(null)
    private set

  var sendColor: Color by mutableStateOf(Color.Unspecified)

  val isShowing: Boolean
    get() = confirmation != null

  /** @param replyHeader Header text saying who the sticker will reply to, or null when it is not a reply. */
  fun show(sticker: KeyboardSticker, replyHeader: String?) {
    update(StickerConfirmation(sticker, replyHeader))
  }

  fun dismiss() {
    update(null)
  }

  /**
   * Drops any confirmation without calling [onShowingChanged], for when the host's views are
   * already being torn down and there is no input panel left to restore.
   */
  fun clear() {
    confirmation = null
  }

  fun send() {
    val sticker = confirmation?.sticker ?: return
    update(null)
    onSend(sticker)
  }

  private fun update(value: StickerConfirmation?) {
    val wasShowing = isShowing
    confirmation = value
    if (wasShowing != isShowing) {
      onShowingChanged(isShowing)
    }
  }
}

data class StickerConfirmation(
  val sticker: KeyboardSticker,
  val replyHeader: String?
)

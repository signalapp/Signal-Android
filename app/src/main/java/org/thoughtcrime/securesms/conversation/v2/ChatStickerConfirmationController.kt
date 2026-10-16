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
 * @param favoritesEnabled Whether the sticker can be added to or removed from favorites from here.
 * @param onToggleFavorite Called to flip the sticker's favorite state, once the user has confirmed a
 *   removal. The host reports the outcome back through [onFavoriteChanged].
 * @param onViewPack Called to open the sticker's pack, after the confirmation has gone away.
 * @param onShowingChanged Called as a confirmation appears or goes away, for the host to get the
 *   input panel out of the way while one shows.
 */
class ChatStickerConfirmationController(
  val favoritesEnabled: Boolean,
  private val onSend: (KeyboardSticker) -> Unit,
  private val onToggleFavorite: (KeyboardSticker) -> Unit,
  private val onViewPack: (KeyboardSticker) -> Unit,
  private val onShowingChanged: (Boolean) -> Unit
) {

  var confirmation: StickerConfirmation? by mutableStateOf(null)
    private set

  var sendColor: Color by mutableStateOf(Color.Unspecified)

  /** Whether the user is being asked to confirm taking the sticker out of favorites. */
  var confirmingRemoveFavorite: Boolean by mutableStateOf(false)
    private set

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
    confirmingRemoveFavorite = false
  }

  fun send() {
    val sticker = confirmation?.sticker ?: return
    update(null)
    onSend(sticker)
  }

  fun toggleFavorite() {
    val sticker = confirmation?.sticker ?: return
    if (sticker.isFavorite) {
      confirmingRemoveFavorite = true
    } else {
      onToggleFavorite(sticker)
    }
  }

  fun confirmRemoveFavorite() {
    confirmingRemoveFavorite = false
    val sticker = confirmation?.sticker ?: return
    onToggleFavorite(sticker)
  }

  fun cancelRemoveFavorite() {
    confirmingRemoveFavorite = false
  }

  fun viewPack() {
    val sticker = confirmation?.sticker ?: return
    update(null)
    onViewPack(sticker)
  }

  /** Reflects a favorite change in the confirmation, if it is still showing that sticker. */
  fun onFavoriteChanged(sticker: KeyboardSticker, favorited: Boolean) {
    val current = confirmation ?: return
    if (current.sticker.packId == sticker.packId && current.sticker.stickerId == sticker.stickerId) {
      confirmation = current.copy(sticker = current.sticker.copy(isFavorite = favorited))
    }
  }

  private fun update(value: StickerConfirmation?) {
    val wasShowing = isShowing
    confirmation = value
    confirmingRemoveFavorite = false
    if (wasShowing != isShowing) {
      onShowingChanged(isShowing)
    }
  }
}

data class StickerConfirmation(
  val sticker: KeyboardSticker,
  val replyHeader: String?
)

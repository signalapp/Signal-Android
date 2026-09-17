/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.data.KeyboardSticker

/**
 * Side effects emitted by a [MediaKeyboard] that need to be handled by the user of the component:
 * what the user picked, and the things only a host can carry out.
 *
 * Requests flow the other way as [MediaKeyboardScreenEvents], and everything about the space the
 * keyboard is given is the scaffold's, as
 * [org.signal.core.ui.compose.keyboard.KeyboardSheetAction].
 */
sealed interface MediaKeyboardAction {

  /** @param emoji The emoji to insert, already resolved to the user's preferred variation. */
  data class EmojiSelected(val emoji: String) : MediaKeyboardAction

  /** Delete backwards from wherever the host's own field has its cursor. */
  data object Backspace : MediaKeyboardAction

  /** @param sticker The sticker to send. */
  data class StickerSelected(val sticker: KeyboardSticker) : MediaKeyboardAction

  /** @param gif The gif to send. */
  data class GifSelected(val gif: KeyboardGif) : MediaKeyboardAction

  /**
   * The user switched tabs. The keyboard has no memory of its own across the times it is shown, so
   * a host that wants to reopen on the tab last picked has to record this.
   *
   * @param tab The tab now showing.
   */
  data class TabSelected(val tab: MediaKeyboardTab) : MediaKeyboardAction

  /** Take the user to wherever sticker packs are managed. */
  data object StickerManagementClicked : MediaKeyboardAction

  /**
   * Take the user to sticker search, which is a screen of its own rather than a field in the
   * keyboard. Emitted by both the sticker page's search field and the top bar's search icon.
   */
  data object StickerSearchClicked : MediaKeyboardAction

  /** Take the user to gif search. As [StickerSearchClicked], but for the gif page. */
  data object GifSearchClicked : MediaKeyboardAction

  /**
   * Show the user everything in a sticker pack.
   *
   * @param packId The pack to open.
   * @param packKey Its key, which identifies the pack alongside [packId].
   */
  data class ViewStickerPackClicked(val packId: String, val packKey: String) : MediaKeyboardAction
}

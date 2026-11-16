/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import org.thoughtcrime.securesms.avatar.Avatars

sealed interface TextAvatarCreationEvent {
  /** The user edited the avatar's text. */
  data class TextChanged(val text: String) : TextAvatarCreationEvent

  /** The user pressed the keyboard's Next key while typing the text. */
  data object KeyboardNextClicked : TextAvatarCreationEvent

  /** The user tapped one of the color swatches. */
  data class ColorSelected(val color: Avatars.ColorPair) : TextAvatarCreationEvent

  /** The user tapped the Text or Color tab. */
  data class TabClicked(val tab: TextAvatarCreationState.Tab) : TextAvatarCreationEvent

  /** The user tapped Done. */
  data object DoneClicked : TextAvatarCreationEvent

  /** The user tapped the toolbar's back arrow. */
  data object NavigateBackClicked : TextAvatarCreationEvent
}

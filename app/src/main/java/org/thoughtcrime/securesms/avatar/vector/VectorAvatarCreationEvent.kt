/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import org.thoughtcrime.securesms.avatar.Avatars

sealed interface VectorAvatarCreationEvent {
  /** The user tapped one of the color swatches. */
  data class ColorSelected(val color: Avatars.ColorPair) : VectorAvatarCreationEvent

  /** The user tapped Done. */
  data object DoneClicked : VectorAvatarCreationEvent

  /** The user tapped the toolbar's back arrow. */
  data object NavigateBackClicked : VectorAvatarCreationEvent
}

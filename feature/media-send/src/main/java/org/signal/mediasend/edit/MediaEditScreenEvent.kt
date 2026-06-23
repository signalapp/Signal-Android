/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import org.signal.core.models.media.Media

sealed interface MediaEditScreenEvent {
  data class FocusedMediaChanged(val media: Media) : MediaEditScreenEvent
  data class AddMessageClick(val startWithEmojiKeyboard: Boolean = false) : MediaEditScreenEvent
}

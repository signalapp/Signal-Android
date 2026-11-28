/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Focuses whatever this was given to. A request made as the target is first composed can be dropped, because its focus
 * target may not be attached until after the first frame, so this waits for that frame first.
 */
suspend fun FocusRequester.requestFocusWhenReady() {
  withFrameNanos { }
  requestFocus()
}

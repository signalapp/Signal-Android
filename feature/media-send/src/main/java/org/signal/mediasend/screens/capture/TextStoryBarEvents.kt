/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

/**
 * The controls the text story editor offers alongside its canvas. The editor itself is still a fragment in the app
 * module, which acts on these directly; only the bar that raises them lives here.
 */
sealed interface TextStoryBarEvents {
  data object CycleBackgroundColor : TextStoryBarEvents
  data object AddLink : TextStoryBarEvents
}

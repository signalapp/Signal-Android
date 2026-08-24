/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Intended to be bound at the app level to allow for the production of an observable single-color representation of a
 * recipient's chat color. Raw types are utilized here since RecipientId and ChatColors are both currently localized to
 * the app module and are non-trivial to pick up and move.
 *
 * Gradient chat colors collapse to a single color, matching what the legacy send button did.
 */
val LocalChatColorProvider = compositionLocalOf<@Composable (Long) -> State<Color>> {
  { remember(it) { mutableStateOf(UnboundChatColor) } }
}

/** Stand-in used in previews and anywhere else the provider is left unbound. Matches the default (ultramarine) bubble. */
private val UnboundChatColor = Color(0xFF315FF4)

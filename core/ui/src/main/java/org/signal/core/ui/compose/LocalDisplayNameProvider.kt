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

/**
 * Intended to be bound at the app level to allow for the production of an observable display name of a recipient. Raw types are utilized here since
 * RecipientId and Recipient are both currently localized to the app module and are non-trivial to pick up and move.
 */
val LocalDisplayNameProvider = compositionLocalOf<@Composable (Long) -> State<String>> {
  { remember(it) { mutableStateOf("DisplayName($it)") } }
}

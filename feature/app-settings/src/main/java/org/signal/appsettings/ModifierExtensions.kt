/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val AppSettingsMaxContentWidth = 624.dp

/**
 * Modifier rules that should be applied to all settings screens for consistency.
 *
 * Scrolling screens should apply this after their scroll modifier, so the scroll container stays full width. Lazy lists
 * can't constrain their items with a modifier, and use [AppSettingsLazyColumn] instead.
 */
fun Modifier.appSettingsScreen(): Modifier {
  return this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AppSettingsMaxContentWidth)
    .fillMaxWidth()
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A [LazyColumn] that scrolls across its full width while keeping its items within the same max width as
 * [appSettingsScreen].
 *
 * @param scaffoldPadding Padding from the enclosing scaffold, applied around the list.
 * @param contentPadding Padding applied within the list, on top of the gutters that center its items.
 */
@Composable
fun AppSettingsLazyColumn(
  modifier: Modifier = Modifier,
  scaffoldPadding: PaddingValues = PaddingValues(),
  contentPadding: PaddingValues = PaddingValues(),
  state: LazyListState = rememberLazyListState(),
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  content: LazyListScope.() -> Unit
) {
  BoxWithConstraints(
    propagateMinConstraints = true,
    modifier = Modifier.padding(scaffoldPadding)
  ) {
    val layoutDirection = LocalLayoutDirection.current
    val gutter = ((maxWidth - AppSettingsMaxContentWidth) / 2).coerceAtLeast(0.dp)

    LazyColumn(
      state = state,
      contentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + gutter,
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection) + gutter,
        bottom = contentPadding.calculateBottomPadding()
      ),
      horizontalAlignment = horizontalAlignment,
      modifier = modifier,
      content = content
    )
  }
}

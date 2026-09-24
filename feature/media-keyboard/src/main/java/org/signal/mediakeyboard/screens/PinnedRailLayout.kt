/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Whether the media keyboard is (or is settling to) full height. Bottom rails re-show themselves
 * when it expands.
 */
val LocalMediaKeyboardExpanded: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

private val SCROLL_DIRECTION_THRESHOLD = 4.dp

/**
 * Lays out [content] with a [rail] pinned to the bottom of this layout.
 *
 * Like browser chrome, the rail slides away when the content is scrolled down and returns when it
 * is scrolled back up or the keyboard grows to full height.
 */
@Composable
internal fun PinnedRailLayout(
  rail: (@Composable () -> Unit)?,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val density = LocalDensity.current
  val expanded = LocalMediaKeyboardExpanded.current

  var measuredRailHeight by remember { mutableIntStateOf(0) }
  var railHidden by remember { mutableStateOf(false) }

  val scrollThresholdPx = with(density) { SCROLL_DIRECTION_THRESHOLD.toPx() }
  val scrollConnection = remember(scrollThresholdPx) {
    object : NestedScrollConnection {
      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
          val delta = if (consumed.y != 0f) consumed.y else available.y
          if (delta < -scrollThresholdPx) {
            railHidden = true
          } else if (delta > scrollThresholdPx) {
            railHidden = false
          }
        }
        return Offset.Zero
      }
    }
  }

  LaunchedEffect(expanded) {
    if (expanded) {
      railHidden = false
    }
  }

  val railHeight = if (rail != null) measuredRailHeight else 0
  val railHiddenFraction by animateFloatAsState(
    targetValue = if (railHidden) 1f else 0f,
    label = "railHiddenFraction"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .clipToBounds()
      .nestedScroll(scrollConnection)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = with(density) { (railHeight * (1f - railHiddenFraction)).toDp() })
    ) {
      content()
    }

    if (rail != null) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .onSizeChanged { measuredRailHeight = it.height }
          .graphicsLayer { translationY = railHeight * railHiddenFraction }
      ) {
        rail()
      }
    }
  }
}

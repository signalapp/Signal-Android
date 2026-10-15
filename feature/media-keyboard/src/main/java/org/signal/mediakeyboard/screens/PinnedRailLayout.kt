/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
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
import org.signal.core.ui.compose.CollapsingBarsState
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.signal.core.ui.compose.collapsingBottomBar
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.theme.SignalTheme

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
 *
 * The rail holds the navigation bar clear on the keyboard's behalf, painting behind it while keeping its own contents
 * above it, so [content] is free to fill the sheet to the bottom of the window. A hidden rail takes the bar's room
 * with it, which is what lets the content run edge to edge once it is scrolled away. With no rail to do that, this
 * layout keeps the bar clear itself, since nothing else in the sheet does.
 *
 * @param barsState Overlays the rail on full-height [content], above the keyboard, collapsing with the host's chrome.
 * @param contentAtTop Shows the rail while the content is scrolled to the top. Ignored with [barsState].
 * @param content Given the bottom padding to leave clear, which is non-zero only with [barsState].
 */
@Composable
internal fun PinnedRailLayout(
  rail: (@Composable () -> Unit)?,
  modifier: Modifier = Modifier,
  barsState: CollapsingBarsState? = null,
  contentAtTop: Boolean = false,
  content: @Composable (contentPadding: PaddingValues) -> Unit
) {
  if (barsState != null) {
    OverlaidRailLayout(rail = rail, barsState = barsState, modifier = modifier, content = content)
  } else {
    InsetRailLayout(rail = rail, contentAtTop = contentAtTop, modifier = modifier, content = { content(PaddingValues()) })
  }
}

@Composable
private fun InsetRailLayout(
  rail: (@Composable () -> Unit)?,
  contentAtTop: Boolean,
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
          // Hiding takes a real scroll; showing takes any pull down, so content that stops scrolling cannot strand it.
          if (consumed.y < -scrollThresholdPx) {
            railHidden = true
          } else if (consumed.y + available.y.coerceAtLeast(0f) > scrollThresholdPx) {
            railHidden = false
          }
        }
        return Offset.Zero
      }
    }
  }

  LaunchedEffect(expanded, contentAtTop) {
    if (expanded || contentAtTop) {
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
    val contentBottomPadding = if (rail != null) {
      with(density) { (railHeight * (1f - railHiddenFraction)).toDp() }
    } else {
      WindowInsets.navigationBarsCompat.asPaddingValues().calculateBottomPadding()
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = contentBottomPadding)
    ) {
      content()
    }

    if (rail != null) {
      RailSurface(
        insets = WindowInsets.navigationBarsCompat,
        rail = rail,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .onSizeChanged { measuredRailHeight = it.height }
          .graphicsLayer { translationY = railHeight * railHiddenFraction }
      )
    }
  }
}

@Composable
private fun OverlaidRailLayout(
  rail: (@Composable () -> Unit)?,
  barsState: CollapsingBarsState,
  modifier: Modifier = Modifier,
  content: @Composable (contentPadding: PaddingValues) -> Unit
) {
  val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBarsCompat)
  val sheet = LocalBottomSheetHostState.current

  Box(
    modifier = modifier
      .fillMaxSize()
      .clipToBounds()
  ) {
    val clearBottom = if (rail != null) {
      with(LocalDensity.current) { barsState.bottomBarHeightPx.toDp() }
    } else {
      bottomInsets.asPaddingValues().calculateBottomPadding()
    }

    content(PaddingValues(bottom = clearBottom))

    if (rail != null) {
      RailSurface(
        insets = bottomInsets,
        rail = rail,
        modifier = Modifier
          .align(Alignment.BottomStart)
          // Kept on screen when an enclosing bottom sheet extends below it.
          .graphicsLayer { translationY = -sheet.overflowBottomPx.toFloat() }
          .collapsingBottomBar(barsState)
      )
    }
  }
}

@Composable
private fun RailSurface(
  insets: WindowInsets,
  rail: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = SignalTheme.colors.colorSurface3,
    modifier = modifier.fillMaxWidth()
  ) {
    Box(modifier = Modifier.windowInsetsPadding(insets)) {
      rail()
    }
  }
}

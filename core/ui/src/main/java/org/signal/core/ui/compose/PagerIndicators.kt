/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

object PagerIndicators {

  fun testTagDot(page: Int): String = "pager-indicator-dot:$page"

  /**
   * A row of dots, one per page of [pagerState]. The dot for the current page takes [activeColor], and the colour follows
   * the scroll as it happens, so a swipe or a tap on another dot fades the highlight across. Tapping a dot scrolls to
   * its page.
   */
  @Composable
  fun Dots(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    dotSize: Dp = 8.dp
  ) {
    val scope = rememberCoroutineScope()

    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier
    ) {
      repeat(pagerState.pageCount) { page ->
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(width = dotSize + 16.dp, height = 32.dp)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = false, radius = 16.dp),
              role = Role.Tab
            ) {
              scope.launch { pagerState.animateScrollToPage(page) }
            }
            .semantics { selected = pagerState.currentPage == page }
            .testTag(testTagDot(page))
        ) {
          Dot(
            pagerState = pagerState,
            page = page,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            dotSize = dotSize
          )
        }
      }
    }
  }

  @Composable
  private fun Dot(
    pagerState: PagerState,
    page: Int,
    activeColor: Color,
    inactiveColor: Color,
    dotSize: Dp
  ) {
    Box(
      modifier = Modifier
        .size(dotSize)
        .drawBehind {
          // How close the scroll is to this page, from 0 when a whole page or more away to 1 when settled on it.
          val focus = (1f - abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - page)).coerceIn(0f, 1f)
          drawCircle(color = lerp(inactiveColor, activeColor, focus))
        }
    )
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun DotsPreview() {
  PagerIndicators.Dots(pagerState = rememberPagerState(initialPage = 1) { 4 })
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun DotsMidScrollPreview() {
  val pagerState = rememberPagerState(initialPage = 1, initialPageOffsetFraction = 0.4f) { 4 }
  PagerIndicators.Dots(pagerState = pagerState, modifier = Modifier.width(240.dp))
}

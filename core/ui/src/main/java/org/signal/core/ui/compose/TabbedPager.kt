/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A row of tabs over a [HorizontalPager], one tab per page and kept in step with it: tapping a tab scrolls to its
 * page, and swiping between pages moves the selected tab.
 *
 * @param tabTitles One title per page, in page order.
 * @param scrollable True to let the tabs size to their titles and scroll, rather than share the width equally and
 *   cut titles short. Better for longer titles, or more tabs than comfortably fit.
 * @param pagerState Hoist this to read or drive the current page from outside.
 * @param beyondViewportPageCount How many pages either side of the visible one stay composed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedPager(
  tabTitles: List<String>,
  modifier: Modifier = Modifier,
  scrollable: Boolean = false,
  pagerState: PagerState = rememberPagerState { tabTitles.size },
  beyondViewportPageCount: Int = 0,
  pageContent: @Composable (page: Int) -> Unit
) {
  val coroutineScope = rememberCoroutineScope()

  val indicator: @Composable TabIndicatorScope.() -> Unit = {
    TabRowDefaults.SecondaryIndicator(
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = true)
    )
  }

  val tabs: @Composable () -> Unit = {
    tabTitles.forEachIndexed { page, title ->
      val selected = pagerState.currentPage == page
      Tab(
        selected = selected,
        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(page) } },
        text = { TabTitle(title = title, selected = selected, ellipsize = !scrollable) },
        modifier = Modifier.testTag(TabbedPagerTestTags.tab(page))
      )
    }
  }

  Column(modifier = modifier) {
    if (scrollable) {
      SecondaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 0.dp,
        indicator = indicator,
        tabs = tabs
      )
    } else {
      SecondaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = indicator,
        tabs = tabs
      )
    }

    HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = beyondViewportPageCount,
      verticalAlignment = Alignment.Top,
      modifier = Modifier.fillMaxSize()
    ) { page ->
      pageContent(page)
    }
  }
}

/**
 * Both weights are laid out on top of each other and only one shown, so a tab is as wide selected as not and the
 * row doesn't shift as the selection moves.
 */
@Composable
private fun TabTitle(
  title: String,
  selected: Boolean,
  ellipsize: Boolean
) {
  val overflow = if (ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip

  Box(contentAlignment = Alignment.Center) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = overflow,
      modifier = Modifier
        .alpha(if (selected) 0f else 1f)
        .clearAndSetSemantics {}
    )

    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = overflow,
      modifier = Modifier.alpha(if (selected) 1f else 0f)
    )
  }
}

@VisibleForTesting
object TabbedPagerTestTags {
  private const val TAB_PREFIX = "tabbed-pager-tab-"

  fun tab(page: Int): String = TAB_PREFIX + page
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun TabbedPagerPreview() {
  TabbedPager(tabTitles = listOf("All", "Recurring", "One-time", "For a friend"), scrollable = true) { page ->
    Box(modifier = Modifier.fillMaxSize()) {
      Text(text = "Page $page")
    }
  }
}

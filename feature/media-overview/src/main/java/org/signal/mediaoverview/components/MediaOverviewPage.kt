/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewRow
import org.signal.mediaoverview.MediaOverviewSharedElements
import org.signal.mediaoverview.MediaOverviewState
import org.signal.mediaoverview.MediaOverviewTestTags
import org.signal.mediaoverview.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val MIN_COLUMNS = 4
private val TargetCellSize = 116.dp

/**
 * One tab's media, as a sectioned grid or list, paged in as it scrolls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaOverviewPage(
  tab: MediaOverviewState.Tab,
  state: MediaOverviewState,
  rows: LazyPagingItems<MediaOverviewRow>,
  sharedElements: MediaOverviewSharedElements,
  onEvent: (MediaOverviewEvent) -> Unit,
  contentPadding: PaddingValues,
  modifier: Modifier = Modifier
) {
  val isLoaded = rows.loadState.refresh is LoadState.NotLoading
  ReportDrawnWhen { isLoaded }

  if (isLoaded && rows.itemCount == 0) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier
        .fillMaxSize()
        .testTag(MediaOverviewTestTags.EMPTY)
    ) {
      Text(
        text = stringResource(R.string.MediaOverviewScreen__no_media),
        fontSize = 24.sp
      )
    }
    return
  }

  val isDetail = state.isDetailLayout(tab)
  val columns = if (isDetail) GridCells.Fixed(1) else MediaGridCells
  val gridSpacing = if (isDetail) 0.dp else 4.dp

  LazyVerticalGrid(
    columns = columns,
    state = rememberLazyGridState(),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(gridSpacing),
    verticalArrangement = Arrangement.spacedBy(gridSpacing),
    modifier = modifier
      .fillMaxSize()
      .testTag(MediaOverviewTestTags.scroller(tab))
  ) {
    fun mediaRun(start: Int, end: Int) {
      if (end <= start) {
        return
      }

      items(
        count = end - start,
        key = { offset -> rowKey(rows.peek(start + offset), start + offset) },
        contentType = { if (isDetail) "detail" else "grid" }
      ) { offset ->
        val item = (rows[start + offset] as? MediaOverviewRow.Media)?.item

        if (item == null) {
          MediaPlaceholder(isDetail = isDetail)
        } else {
          MediaCell(
            item = item,
            tab = tab,
            state = state,
            isDetail = isDetail,
            sharedElements = sharedElements,
            onEvent = onEvent
          )
        }
      }
    }

    var runStart = 0
    for (index in 0 until rows.itemCount) {
      val row = rows.peek(index)
      if (row is MediaOverviewRow.Header) {
        mediaRun(runStart, index)
        stickyHeader(key = rowKey(row, index), contentType = "header") {
          // Reading the row through get() tells the pager which position is on screen so it loads around it.
          rows[index]
          SectionHeader(header = row.header)
        }
        runStart = index + 1
      }
    }
    mediaRun(runStart, rows.itemCount)
  }
}

@Composable
private fun MediaCell(
  item: MediaOverviewItem,
  tab: MediaOverviewState.Tab,
  state: MediaOverviewState,
  isDetail: Boolean,
  sharedElements: MediaOverviewSharedElements,
  onEvent: (MediaOverviewEvent) -> Unit
) {
  val isSelected = item.key in state.selectedKeys
  val isContextMenuOpen = state.contextMenuKey == item.key && tab == state.selectedTab

  if (isDetail) {
    MediaDetailRow(
      item = item,
      isSelected = isSelected,
      showThread = state.isAllThreads,
      isContextMenuOpen = isContextMenuOpen,
      audioPlayback = state.audioPlayback,
      onEvent = onEvent
    )
  } else {
    MediaGalleryCell(
      item = item,
      isSelected = isSelected,
      showFileSize = state.showFileSizes,
      isContextMenuOpen = isContextMenuOpen,
      sharedElements = sharedElements,
      onEvent = onEvent
    )
  }
}

@Composable
private fun MediaPlaceholder(isDetail: Boolean) {
  if (isDetail) {
    Box(modifier = Modifier.fillMaxWidth().height(72.dp))
  } else {
    Box(
      modifier = Modifier
        .aspectRatio(1f)
        .background(SignalTheme.colors.colorSurface2)
    )
  }
}

/** At least [MIN_COLUMNS] columns, with more added as the width fits cells of about [TargetCellSize]. */
private object MediaGridCells : GridCells {
  override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
    val count = maxOf(MIN_COLUMNS, (availableSize + spacing) / (TargetCellSize.roundToPx() + spacing))
    val cellsSize = availableSize - spacing * (count - 1)
    val cellSize = cellsSize / count
    val remainder = cellsSize % count
    return List(count) { index -> cellSize + if (index < remainder) 1 else 0 }
  }
}

@Composable
private fun SectionHeader(header: MediaOverviewState.SectionHeader) {
  Text(
    text = headerText(header),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .heightIn(min = 48.dp)
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
  )
}

@Composable
private fun headerText(header: MediaOverviewState.SectionHeader): String {
  return when (header) {
    MediaOverviewState.SectionHeader.Today -> {
      stringResource(R.string.MediaOverviewScreen__today)
    }
    MediaOverviewState.SectionHeader.Yesterday -> {
      stringResource(R.string.MediaOverviewScreen__yesterday)
    }
    MediaOverviewState.SectionHeader.ThisWeek -> {
      stringResource(R.string.MediaOverviewScreen__this_week)
    }
    MediaOverviewState.SectionHeader.ThisMonth -> {
      stringResource(R.string.MediaOverviewScreen__this_month)
    }
    MediaOverviewState.SectionHeader.Small -> {
      stringResource(R.string.MediaOverviewScreen__small)
    }
    MediaOverviewState.SectionHeader.Medium -> {
      stringResource(R.string.MediaOverviewScreen__medium)
    }
    MediaOverviewState.SectionHeader.Large -> {
      stringResource(R.string.MediaOverviewScreen__large)
    }
    is MediaOverviewState.SectionHeader.Month -> {
      val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
      val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, header.year)
        set(Calendar.MONTH, header.month)
        set(Calendar.DAY_OF_MONTH, 1)
      }
      SimpleDateFormat("MMMM, yyyy", locale).format(calendar.time)
    }
  }
}

/** Placeholders are keyed by position, since their content is not known yet. */
private fun rowKey(row: MediaOverviewRow?, index: Int): String {
  return when (row) {
    is MediaOverviewRow.Header -> {
      "header-${MediaOverviewTestTags.keyString(row.firstItemKey)}"
    }
    is MediaOverviewRow.Media -> {
      MediaOverviewTestTags.keyString(row.item.key)
    }
    null -> {
      "placeholder-$index"
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.ByteSize
import org.signal.mediaoverview.components.BottomBarItem
import org.signal.mediaoverview.components.MediaOverviewBottomBar
import org.signal.mediaoverview.components.MediaOverviewPage
import kotlin.math.abs
import org.signal.core.ui.R as CoreUiR

object MediaOverviewTestTags {
  const val SORT_BUTTON = "sort-button"
  const val LAYOUT_TOGGLE = "layout-toggle"
  const val EMPTY = "empty"
  const val BOTTOM_BAR_SAVE = "bottom-bar-save"
  const val BOTTOM_BAR_SELECT_ALL = "bottom-bar-select-all"
  const val BOTTOM_BAR_DELETE = "bottom-bar-delete"
  const val CONTEXT_MENU_FORWARD = "context-menu-forward"
  const val CONTEXT_MENU_SHARE = "context-menu-share"
  const val CONTEXT_MENU_SAVE = "context-menu-save"
  const val CONTEXT_MENU_DELETE = "context-menu-delete"
  const val CONTEXT_MENU_SELECT = "context-menu-select"
  const val CONTEXT_MENU_JUMP_TO_MESSAGE = "context-menu-jump-to-message"

  fun tab(tab: MediaOverviewState.Tab): String = "tab-$tab"

  fun scroller(tab: MediaOverviewState.Tab): String = "scroller-$tab"

  fun item(key: MediaOverviewItem.Key): String = "item-${keyString(key)}"

  fun audioButton(key: MediaOverviewItem.Key): String = "audio-button-${keyString(key)}"

  internal fun keyString(key: MediaOverviewItem.Key): String {
    return when (key) {
      is MediaOverviewItem.Key.Attachment -> {
        "a${key.attachmentId.id}"
      }
      is MediaOverviewItem.Key.Message -> {
        "m${key.messageId}"
      }
    }
  }
}

/**
 * Every piece of media in a conversation (or across all of them), split into tabs by type.
 *
 * @param media Each tab's paged rows. A tab missing from the map shows as empty.
 * @param sharedElements Collects the grid's thumbnail views so the host can animate into the media viewer.
 */
@Composable
fun MediaOverviewScreen(
  state: MediaOverviewState,
  media: Map<MediaOverviewState.Tab, Flow<PagingData<MediaOverviewRow>>>,
  onEvent: (MediaOverviewEvent) -> Unit,
  modifier: Modifier = Modifier,
  sharedElements: MediaOverviewSharedElements = remember { MediaOverviewSharedElements() }
) {
  val tabs = state.tabs
  val pagerState = rememberPagerState(initialPage = tabs.indexOf(state.selectedTab).coerceAtLeast(0)) { tabs.size }
  val currentOnEvent by rememberUpdatedState(onEvent)
  val startedTabs = remember { mutableSetOf<MediaOverviewState.Tab>() }

  LaunchedEffect(pagerState, tabs) {
    snapshotFlow { pagerState.settledPage }.collect { page ->
      tabs.getOrNull(page)?.let { currentOnEvent(MediaOverviewEvent.TabSelected(it)) }
    }
  }

  BackHandler(enabled = state.isMultiSelect) {
    onEvent(MediaOverviewEvent.SelectionCleared)
  }

  Scaffolds.Settings(
    title = if (state.isMultiSelect) {
      pluralStringResource(R.plurals.MediaOverviewScreen__d_selected_s, state.selectedKeys.size, state.selectedKeys.size, ByteSize(state.selectedTotalSize).toUnitString())
    } else if (state.isAllThreads) {
      stringResource(R.string.MediaOverviewScreen__all_storage_use)
    } else {
      state.title
    },
    onNavigationClick = {
      if (state.isMultiSelect) {
        onEvent(MediaOverviewEvent.SelectionCleared)
      } else {
        onEvent(MediaOverviewEvent.BackClicked)
      }
    },
    navigationIcon = if (state.isMultiSelect) SignalIcons.X.imageVector else SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = if (state.isMultiSelect) stringResource(CoreUiR.string.CloseButton__content_description) else null,
    modifier = modifier
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize()
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        MediaTabRow(
          tabs = tabs,
          pagerState = pagerState,
          enabled = !state.isMultiSelect
        )

        SortRow(state = state, onEvent = onEvent)

        HorizontalPager(
          state = pagerState,
          userScrollEnabled = !state.isMultiSelect,
          verticalAlignment = Alignment.Top,
          modifier = Modifier.fillMaxSize()
        ) { page ->
          val tab = tabs[page]
          val started = tab in startedTabs || pagerState.shouldLoad(page)
          if (started) {
            SideEffect { startedTabs += tab }
          }

          MediaOverviewPage(
            tab = tab,
            state = state,
            rows = (if (started) media[tab] ?: NoMedia else NotStarted).collectAsLazyPagingItems(),
            sharedElements = sharedElements,
            onEvent = onEvent,
            contentPadding = PaddingValues(bottom = 160.dp)
          )
        }
      }

      MediaOverviewBottomBar(
        visible = state.isMultiSelect,
        items = bottomBarItems(state, onEvent),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(start = 16.dp, end = 16.dp, bottom = 36.dp)
      )
    }

    MediaOverviewDialog(state = state, onEvent = onEvent)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTabRow(
  tabs: List<MediaOverviewState.Tab>,
  pagerState: PagerState,
  enabled: Boolean
) {
  val coroutineScope = rememberCoroutineScope()

  Box(modifier = Modifier.fillMaxWidth()) {
    SecondaryTabRow(
      selectedTabIndex = pagerState.currentPage,
      containerColor = MaterialTheme.colorScheme.surface,
      indicator = {
        TabRowDefaults.SecondaryIndicator(
          height = 3.dp,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .tabIndicatorOffset(pagerState.currentPage, matchContentSize = false)
            .wrapContentWidth()
            .width(40.dp)
            .clip(CircleShape)
        )
      },
      divider = {},
      modifier = Modifier
        .align(Alignment.TopCenter)
        .widthIn(max = MaxTabWidth * tabs.size)
    ) {
      tabs.forEachIndexed { index, tab ->
        val selected = pagerState.currentPage == index
        Tab(
          selected = selected,
          enabled = enabled,
          onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
          text = {
            Text(
              text = tabTitle(tab),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1
            )
          },
          modifier = Modifier.testTag(MediaOverviewTestTags.tab(tab))
        )
      }
    }

    HorizontalDivider(
      thickness = 1.dp,
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}

@Composable
private fun SortRow(
  state: MediaOverviewState,
  onEvent: (MediaOverviewEvent) -> Unit
) {
  val toggleAlpha by animateFloatAsState(targetValue = if (state.isLayoutToggleVisible) 1f else 0f, label = "toggle-alpha")

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp, bottom = 2.dp)
      .height(56.dp)
      .padding(start = 16.dp, end = 4.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .clickable { onEvent(MediaOverviewEvent.SortClicked) }
        .padding(vertical = 8.dp)
        .testTag(MediaOverviewTestTags.SORT_BUTTON)
    ) {
      Text(
        text = sortingLabel(state.sorting),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.width(8.dp))
      Icon(
        painter = painterResource(R.drawable.symbol_arrow_down_compact_bold_16),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(16.dp)
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    IconButton(
      onClick = { onEvent(MediaOverviewEvent.LayoutToggled) },
      enabled = state.isLayoutToggleVisible,
      modifier = Modifier
        .alpha(toggleAlpha)
        .testTag(MediaOverviewTestTags.LAYOUT_TOGGLE)
    ) {
      if (state.detailLayout) {
        Icon(
          painter = painterResource(R.drawable.symbol_grid_square_24),
          contentDescription = stringResource(R.string.MediaOverviewScreen__grid_view),
          tint = MaterialTheme.colorScheme.onSurface
        )
      } else {
        Icon(
          painter = painterResource(R.drawable.symbol_list_bullet_24),
          contentDescription = stringResource(R.string.MediaOverviewScreen__list_view),
          tint = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}

@Composable
private fun bottomBarItems(
  state: MediaOverviewState,
  onEvent: (MediaOverviewEvent) -> Unit
): List<BottomBarItem> {
  val count = state.selectedKeys.size

  return listOf(
    BottomBarItem(
      icon = SignalIcons.Save.imageVector,
      label = pluralStringResource(R.plurals.MediaOverviewScreen__save_plural, count),
      testTag = MediaOverviewTestTags.BOTTOM_BAR_SAVE,
      onClick = { onEvent(MediaOverviewEvent.SaveSelectedClicked) }
    ),
    BottomBarItem(
      icon = SignalIcons.CheckCircle.imageVector,
      label = stringResource(R.string.MediaOverviewScreen__select_all),
      testTag = MediaOverviewTestTags.BOTTOM_BAR_SELECT_ALL,
      onClick = { onEvent(MediaOverviewEvent.SelectAllClicked) }
    ),
    BottomBarItem(
      icon = SignalIcons.Trash.imageVector,
      label = pluralStringResource(R.plurals.MediaOverviewScreen__delete_plural, count),
      testTag = MediaOverviewTestTags.BOTTOM_BAR_DELETE,
      onClick = { onEvent(MediaOverviewEvent.DeleteSelectedClicked) }
    )
  )
}

@Composable
private fun MediaOverviewDialog(
  state: MediaOverviewState,
  onEvent: (MediaOverviewEvent) -> Unit
) {
  when (val dialog = state.dialog) {
    MediaOverviewState.Dialog.None -> {}
    MediaOverviewState.Dialog.SortOrder -> {
      val labels = MediaOverviewState.Sorting.entries.map { sortingLabel(it) }.toTypedArray()
      Dialogs.RadioListDialog(
        onDismissRequest = { onEvent(MediaOverviewEvent.DialogDismissed) },
        title = stringResource(R.string.MediaOverviewScreen__sort_by),
        labels = labels,
        values = MediaOverviewState.Sorting.entries.map { it.name }.toTypedArray(),
        selectedIndex = state.sorting.ordinal,
        onSelected = { onEvent(MediaOverviewEvent.SortSelected(MediaOverviewState.Sorting.entries[it])) }
      )
    }
    is MediaOverviewState.Dialog.ConfirmDelete -> {
      val count = dialog.keys.size
      Dialogs.SimpleAlertDialog(
        title = pluralStringResource(R.plurals.MediaOverviewScreen__delete_confirm_title, count, count),
        body = pluralStringResource(R.plurals.MediaOverviewScreen__delete_confirm_message, count, count),
        confirm = stringResource(R.string.MediaOverviewScreen__delete),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(MediaOverviewEvent.DeleteConfirmed) },
        onDeny = { onEvent(MediaOverviewEvent.DialogDismissed) },
        onDismissRequest = { onEvent(MediaOverviewEvent.DialogDismissed) }
      )
    }
    is MediaOverviewState.Dialog.DeleteProgress -> {
      DeleteProgressDialog(dialog)
    }
    MediaOverviewState.Dialog.SelectAllLoading -> {
      Dialogs.IndeterminateProgressDialog()
    }
    MediaOverviewState.Dialog.AllOffloaded -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.MediaOverviewScreen__cant_save_media),
        body = stringResource(R.string.MediaOverviewScreen__media_offloaded_message),
        confirm = stringResource(android.R.string.ok),
        dismiss = stringResource(R.string.MediaOverviewScreen__view_storage_settings),
        onConfirm = { onEvent(MediaOverviewEvent.DialogDismissed) },
        onDeny = { onEvent(MediaOverviewEvent.ViewStorageSettingsClicked) },
        onDismissRequest = { onEvent(MediaOverviewEvent.DialogDismissed) }
      )
    }
    is MediaOverviewState.Dialog.PartiallyOffloaded -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.MediaOverviewScreen__cant_save_all_items),
        body = stringResource(R.string.MediaOverviewScreen__some_media_offloaded_message),
        confirm = stringResource(android.R.string.ok),
        dismiss = stringResource(R.string.MediaOverviewScreen__view_storage_settings),
        onConfirm = { onEvent(MediaOverviewEvent.SaveAvailableConfirmed) },
        onDeny = { onEvent(MediaOverviewEvent.ViewStorageSettingsClicked) },
        onDismissRequest = { onEvent(MediaOverviewEvent.DialogDismissed) }
      )
    }
  }
}

@Composable
private fun DeleteProgressDialog(dialog: MediaOverviewState.Dialog.DeleteProgress) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      modifier = Modifier.width(280.dp)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
      ) {
        Text(
          text = stringResource(R.string.MediaOverviewScreen__deleting),
          style = MaterialTheme.typography.titleMedium
        )

        if (dialog.total > 0) {
          CircularProgressIndicator(
            progress = { dialog.processed.toFloat() / dialog.total },
            modifier = Modifier.size(56.dp)
          )
        } else {
          CircularProgressIndicator(modifier = Modifier.size(56.dp))
        }

        Text(
          text = stringResource(R.string.MediaOverviewScreen__delete_progress_count, dialog.processed, dialog.total.coerceAtLeast(dialog.processed)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun tabTitle(tab: MediaOverviewState.Tab): String {
  return when (tab) {
    MediaOverviewState.Tab.MEDIA -> {
      stringResource(R.string.MediaOverviewScreen__tab_media)
    }
    MediaOverviewState.Tab.FILES -> {
      stringResource(R.string.MediaOverviewScreen__tab_files)
    }
    MediaOverviewState.Tab.AUDIO -> {
      stringResource(R.string.MediaOverviewScreen__tab_audio)
    }
    MediaOverviewState.Tab.LINKS -> {
      stringResource(R.string.MediaOverviewScreen__tab_links)
    }
    MediaOverviewState.Tab.ALL -> {
      stringResource(R.string.MediaOverviewScreen__tab_all)
    }
  }
}

@Composable
private fun sortingLabel(sorting: MediaOverviewState.Sorting): String {
  return when (sorting) {
    MediaOverviewState.Sorting.NEWEST -> {
      stringResource(R.string.MediaOverviewScreen__newest)
    }
    MediaOverviewState.Sorting.OLDEST -> {
      stringResource(R.string.MediaOverviewScreen__oldest)
    }
    MediaOverviewState.Sorting.LARGEST -> {
      stringResource(R.string.MediaOverviewScreen__storage_used)
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenGridPreview() {
  Previews.Preview {
    MediaOverviewScreen(
      state = MediaOverviewPreviewFixtures.state(),
      media = previewMedia(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenListPreview() {
  Previews.Preview {
    MediaOverviewScreen(
      state = MediaOverviewPreviewFixtures.state(detailLayout = true),
      media = previewMedia(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenMultiSelectPreview() {
  Previews.Preview {
    val state = MediaOverviewPreviewFixtures.state()
    MediaOverviewScreen(
      state = state.copy(selection = MediaOverviewPreviewFixtures.items(MediaOverviewState.Tab.MEDIA).take(1).associate { it.key to it.size }),
      media = previewMedia(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenAllStoragePreview() {
  Previews.Preview {
    MediaOverviewScreen(
      state = MediaOverviewPreviewFixtures.state(isAllThreads = true, selectedTab = MediaOverviewState.Tab.ALL),
      media = previewMedia(isAllThreads = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenEmptyPreview() {
  Previews.Preview {
    MediaOverviewScreen(
      state = MediaOverviewState(title = "Alice"),
      media = emptyMap(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaOverviewScreenDeleteProgressPreview() {
  Previews.Preview {
    MediaOverviewScreen(
      state = MediaOverviewPreviewFixtures.state(dialog = MediaOverviewState.Dialog.DeleteProgress(processed = 12, total = 40)),
      media = previewMedia(),
      onEvent = {}
    )
  }
}

@Composable
private fun previewMedia(isAllThreads: Boolean = false): Map<MediaOverviewState.Tab, Flow<PagingData<MediaOverviewRow>>> {
  return remember(isAllThreads) { MediaOverviewPreviewFixtures.media(isAllThreads) }
}

private val MaxTabWidth = 100.dp

/**
 * Whether [page] should start loading: the page the pager is on or heading to, and its neighbours
 * while dragging, but not the pages a tapped tab's scroll animation passes over.
 */
private fun PagerState.shouldLoad(page: Int): Boolean {
  return page == settledPage || page == targetPage || (abs(targetPage - settledPage) <= 1 && abs(page - settledPage) == 1)
}

private val NotStarted: Flow<PagingData<MediaOverviewRow>> = flowOf(
  PagingData.empty(
    sourceLoadStates = LoadStates(
      refresh = LoadState.Loading,
      prepend = LoadState.NotLoading(endOfPaginationReached = false),
      append = LoadState.NotLoading(endOfPaginationReached = false)
    )
  )
)

private val NoMedia: Flow<PagingData<MediaOverviewRow>> = flowOf(
  PagingData.empty(
    sourceLoadStates = LoadStates(
      refresh = LoadState.NotLoading(endOfPaginationReached = true),
      prepend = LoadState.NotLoading(endOfPaginationReached = true),
      append = LoadState.NotLoading(endOfPaginationReached = true)
    )
  )
)

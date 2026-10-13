/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.manage

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.list.ReorderListEvent
import org.signal.core.ui.compose.list.ReorderableItem
import org.signal.core.ui.compose.list.rememberReorderableListState
import org.signal.core.ui.compose.list.reorderableList
import org.signal.core.ui.compose.showSnackbar
import org.signal.core.ui.getWindowSizeClass
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory
import org.thoughtcrime.securesms.stickers.manage.StickerManagementScreen.MAX_PREVIEW_STICKERS
import org.thoughtcrime.securesms.stickers.manage.StickerPack.DownloadStatus
import java.text.NumberFormat
import org.signal.core.ui.R as CoreUiR

object StickerManagementScreen {

  const val MAX_PREVIEW_STICKERS = 5

  /**
   * Shows the screen as a bottom sheet on large devices (tablets/foldables), activity on phones.
   */
  @JvmStatic
  fun show(activity: FragmentActivity) {
    if (showAsBottomSheet(activity.resources)) {
      StickerManagementBottomSheet.show(activity.supportFragmentManager)
    } else {
      activity.startActivity(StickerManagementActivity.createIntent(activity))
    }
  }

  /**
   * Shows the screen as a bottom sheet on large devices (tablets/foldables), activity on phones.
   */
  fun show(fragment: Fragment) {
    if (showAsBottomSheet(fragment.resources)) {
      StickerManagementBottomSheet.show(fragment.parentFragmentManager)
    } else {
      fragment.startActivity(StickerManagementActivity.createIntent(fragment.requireContext()))
    }
  }

  private fun showAsBottomSheet(resources: Resources): Boolean {
    return resources.getWindowSizeClass().isAtLeastBreakpoint(
      widthDpBreakpoint = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
      heightDpBreakpoint = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    )
  }
}

private data class Page(
  val title: String,
  val getContent: @Composable () -> Unit
)

interface StickerManagementContentCallbacks {
  fun onForwardClick(pack: StickerPack)
  fun onInstallClick(pack: StickerPack)
  fun onShowPreviewClick(pack: StickerPack)
  fun onCopyClick(pack: StickerPack)
  fun onRemoveClick(packIds: Set<StickerPackId>)
  fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>)
  fun onRemoveStickerPacksCanceled()
  fun onSelectionToggle(pack: StickerPack)
  fun onSelectAllToggle()
  fun onReorderableEvent(event: ReorderListEvent)

  object Empty : StickerManagementContentCallbacks {
    override fun onForwardClick(pack: StickerPack) = Unit
    override fun onInstallClick(pack: StickerPack) = Unit
    override fun onShowPreviewClick(pack: StickerPack) = Unit
    override fun onCopyClick(pack: StickerPack) = Unit
    override fun onRemoveClick(packIds: Set<StickerPackId>) = Unit
    override fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>) = Unit
    override fun onRemoveStickerPacksCanceled() = Unit
    override fun onSelectionToggle(pack: StickerPack) = Unit
    override fun onSelectAllToggle() = Unit
    override fun onReorderableEvent(event: ReorderListEvent) = Unit
  }
}

/**
 * Displays all the sticker packs we know about, along with the installed ones, enabling installation, uninstallation, and sorting.
 *
 * @see StickerManagementActivity
 * @see StickerManagementBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManagementScreen(
  uiState: StickerManagementUiState,
  showNavigateBack: Boolean = true,
  applyWindowInsets: Boolean = true,
  onNavigateBack: () -> Unit = {},
  onSetMultiSelectModeEnabled: (Boolean) -> Unit = {},
  onSetSearchModeEnabled: (Boolean) -> Unit = {},
  onSearchQueryChange: (String) -> Unit = {},
  onSnackbarDismiss: () -> Unit = {},
  callbacks: StickerManagementContentCallbacks = StickerManagementContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  val pagerState = rememberPagerState(pageCount = { 2 })
  val coroutineScope = rememberCoroutineScope()

  val pages = listOf(
    Page(
      title = stringResource(R.string.StickerManagement_all_tab_label),
      getContent = {
        AllStickersContent(
          blessedPacks = uiState.filteredBlessedPacks,
          notBlessedPacks = uiState.filteredNotBlessedPacks,
          searchQuery = if (uiState.searchActive) uiState.searchQuery else null,
          callbacks = callbacks
        )
      }
    ),
    Page(
      title = stringResource(R.string.StickerManagement_my_stickers_tab_label),
      getContent = {
        InstalledStickersContent(
          packs = uiState.filteredInstalledPacks,
          multiSelectEnabled = uiState.multiSelectEnabled,
          selectedPackIds = uiState.selectedPackIds,
          searchQuery = if (uiState.searchActive) uiState.searchQuery else null,
          onAddStickersClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
          callbacks = callbacks
        )
      }
    )
  )

  BackHandler(enabled = uiState.multiSelectEnabled || uiState.searchMode) {
    if (uiState.multiSelectEnabled) {
      onSetMultiSelectModeEnabled(false)
    } else {
      onSetSearchModeEnabled(false)
    }
  }

  Scaffold(
    contentWindowInsets = if (applyWindowInsets) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
    modifier = if (applyWindowInsets) Modifier else Modifier.consumeWindowInsets(WindowInsets.systemBars),
    topBar = {
      val isInstalledTabActive = pagerState.currentPage == 1
      when {
        isInstalledTabActive && uiState.multiSelectEnabled -> MultiSelectTopAppBar(
          selectedItemCount = uiState.selectedPackIds.size,
          onExitClick = { onSetMultiSelectModeEnabled(false) }
        )

        uiState.searchMode -> SearchTopAppBar(
          query = uiState.searchQuery,
          onQueryChange = onSearchQueryChange,
          onCloseClick = { onSetSearchModeEnabled(false) }
        )

        else -> TopAppBar(
          showNavigateBack = showNavigateBack,
          onBackPress = onNavigateBack,
          showMenuButton = isInstalledTabActive,
          onSearchClick = { onSetSearchModeEnabled(true) },
          onSetMultiSelectModeEnabled = onSetMultiSelectModeEnabled
        )
      }
    },
    snackbarHost = {
      SnackbarHost(
        actionConfirmation = uiState.actionConfirmation,
        onSnackbarDismiss = onSnackbarDismiss
      )
    }
  ) { padding ->
    if (uiState.userPrompt != null) {
      val packCount = uiState.userPrompt.packIds.size

      Dialogs.SimpleAlertDialog(
        title = pluralStringResource(R.plurals.StickerManagement_delete_n_packs_confirmation, packCount, NumberFormat.getInstance().format(packCount)),
        body = pluralStringResource(R.plurals.StickerManagement_delete_n_packs_confirmation_body, packCount, NumberFormat.getInstance().format(packCount)),
        confirm = stringResource(R.string.StickerManagement_menu_remove_pack),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { callbacks.onRemoveStickerPacksConfirmed(uiState.userPrompt.packIds) },
        onDeny = { callbacks.onRemoveStickerPacksCanceled() },
        onDismissRequest = { callbacks.onRemoveStickerPacksCanceled() }
      )
    }

    Column(
      modifier = modifier.padding(padding)
    ) {
      SecondaryTabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = {
          TabRowDefaults.SecondaryIndicator(
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.tabIndicatorOffset(pagerState.currentPage)
          )
        }
      ) {
        repeat(pages.size) { pageIndex ->
          PagerTab(
            title = pages[pageIndex].title,
            selected = pagerState.currentPage == pageIndex,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pageIndex) } },
            modifier = Modifier.weight(1f)
          )
        }
      }

      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1
      ) { pageIndex ->
        pages[pageIndex].getContent()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
  showNavigateBack: Boolean = true,
  showMenuButton: Boolean = false,
  onBackPress: () -> Unit,
  onSearchClick: () -> Unit,
  onSetMultiSelectModeEnabled: (Boolean) -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = stringResource(R.string.StickerManagement_title_stickers),
    titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
    navigationIconContent = {
      if (showNavigateBack) {
        IconButton(
          onClick = onBackPress,
          modifier = Modifier.padding(end = 16.dp)
        ) {
          Icon(
            imageVector = SignalIcons.ArrowStart.imageVector,
            contentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
          )
        }
      } else {
        Spacer(modifier = Modifier.padding(end = 16.dp))
      }
    },
    actions = {
      IconButton(
        onClick = onSearchClick,
        modifier = Modifier.padding(horizontal = if (showMenuButton) 0.dp else 8.dp)
      ) {
        Icon(
          imageVector = SignalIcons.Search.imageVector,
          contentDescription = stringResource(R.string.StickerManagement_search)
        )
      }

      if (showMenuButton) {
        val menuController = remember { DropdownMenus.MenuController() }
        IconButton(
          onClick = { menuController.show() },
          modifier = Modifier.padding(horizontal = 8.dp)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
            contentDescription = stringResource(R.string.StickerManagement_accessibility_open_top_bar_menu)
          )
        }

        DropdownMenus.Menu(
          controller = menuController,
          offsetX = 24.dp,
          offsetY = 0.dp
        ) {
          DropdownMenus.Item(
            text = { Text(text = stringResource(R.string.StickerManagement_menu_select_packs)) },
            onClick = {
              onSetMultiSelectModeEnabled(true)
              menuController.hide()
            }
          )
        }
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectTopAppBar(
  selectedItemCount: Int,
  onExitClick: () -> Unit = {}
) {
  Scaffolds.DefaultTopAppBar(
    title = pluralStringResource(R.plurals.StickerManagement_title_n_selected, selectedItemCount, NumberFormat.getInstance().format(selectedItemCount)),
    titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = SignalIcons.X.imageVector,
    navigationContentDescription = stringResource(R.string.StickerManagement_accessibility_exit_multi_select_mode),
    onNavigationClick = onExitClick
  )
}

@Composable
private fun SearchTopAppBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onCloseClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .windowInsetsPadding(TopAppBarDefaults.windowInsets)
  ) {
    TextFields.TextField(
      value = query,
      onValueChange = onQueryChange,
      leadingIcon = {
        IconButton(onClick = onCloseClick) {
          Icon(
            imageVector = SignalIcons.ArrowStart.imageVector,
            contentDescription = stringResource(R.string.StickerManagement_accessibility_close_search)
          )
        }
      },
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(
              imageVector = SignalIcons.X.imageVector,
              contentDescription = stringResource(R.string.StickerManagement_accessibility_clear_search)
            )
          }
        }
      },
      placeholder = { Text(text = stringResource(R.string.StickerManagement_search)) },
      textStyle = MaterialTheme.typography.bodyLarge,
      singleLine = true,
      shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(0.dp),
      colors = TextFieldDefaults.colors(
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      modifier = Modifier
        .height(dimensionResource(R.dimen.signal_m3_toolbar_height))
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)
    )
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@Composable
private fun PagerTab(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Tab(
    text = {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    },
    selected = selected,
    onClick = onClick,
    modifier = modifier
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllStickersContent(
  blessedPacks: List<StickerPack>,
  notBlessedPacks: List<StickerPack>,
  searchQuery: String? = null,
  callbacks: StickerManagementContentCallbacks = StickerManagementContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  var expand by remember { mutableStateOf(false) }

  if (blessedPacks.isEmpty() && notBlessedPacks.isEmpty()) {
    EmptyView(
      text = if (searchQuery != null) {
        stringResource(R.string.StickerManagement_search_no_results_s, searchQuery)
      } else {
        stringResource(R.string.StickerManagement_all_tab_empty_text)
      }
    )
  } else {
    val haptics = LocalHapticFeedback.current

    LazyColumn(
      contentPadding = PaddingValues(top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = modifier.fillMaxHeight()
    ) {
      if (blessedPacks.isNotEmpty()) {
        item(key = "blessed_section_header") {
          StickerPackSectionHeader(
            text = stringResource(R.string.StickerManagement_signal_artist_series_header),
            modifier = Modifier.animateItem()
          )
        }

        items(
          items = if (expand || notBlessedPacks.isEmpty()) blessedPacks else blessedPacks.take(MAX_PREVIEW_STICKERS),
          key = { it.id.value }
        ) { pack ->
          val menuController = remember { DropdownMenus.MenuController() }

          StickerPackRow(
            pack = pack,
            menuController = menuController,
            onForwardClick = callbacks::onForwardClick,
            onInstallClick = callbacks::onInstallClick,
            onCopyClick = callbacks::onCopyClick,
            onRemoveClick = { callbacks.onRemoveClick(setOf(it.id)) },
            modifier = Modifier
              .animateItem()
              .combinedClickable(
                onClick = { callbacks.onShowPreviewClick(pack) },
                onLongClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuController.show()
                },
                onLongClickLabel = stringResource(R.string.StickerManagement_accessibility_open_context_menu)
              )
          )
        }

        if (!expand && notBlessedPacks.isNotEmpty() && blessedPacks.size > MAX_PREVIEW_STICKERS) {
          item {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clickable { expand = true }
                .horizontalGutters()
                .fillMaxWidth()
            ) {
              Image(
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                imageVector = ImageVector.vectorResource(id = R.drawable.symbol_chevron_down_24),
                contentDescription = stringResource(R.string.StickerManagement__see_all),
                modifier = Modifier
                  .size(40.dp)
                  .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                  .padding(8.dp)
              )
              Text(
                text = stringResource(R.string.StickerManagement__see_all),
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      }

      if (blessedPacks.isNotEmpty() && notBlessedPacks.isNotEmpty()) {
        item { Dividers.Default() }
      }

      if (notBlessedPacks.isNotEmpty()) {
        item(key = "not_blessed_section_header") {
          StickerPackSectionHeader(
            text = stringResource(R.string.StickerManagement_shared_with_you_header),
            modifier = Modifier.animateItem(),
            description = stringResource(R.string.StickerManagement_when_you_receive)
          )
        }
        items(
          items = notBlessedPacks,
          key = { it.id.value }
        ) { pack ->
          val menuController = remember { DropdownMenus.MenuController() }
          StickerPackRow(
            pack = pack,
            menuController = menuController,
            onForwardClick = callbacks::onForwardClick,
            onInstallClick = callbacks::onInstallClick,
            onCopyClick = callbacks::onCopyClick,
            onRemoveClick = { callbacks.onRemoveClick(setOf(it.id)) },
            modifier = Modifier
              .animateItem()
              .combinedClickable(
                onClick = { callbacks.onShowPreviewClick(pack) },
                onLongClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuController.show()
                }
              )
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstalledStickersContent(
  packs: List<StickerPack>,
  multiSelectEnabled: Boolean,
  selectedPackIds: Set<StickerPackId>,
  searchQuery: String? = null,
  callbacks: StickerManagementContentCallbacks = StickerManagementContentCallbacks.Empty,
  onAddStickersClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  if (packs.isEmpty() && searchQuery != null) {
    EmptyView(text = stringResource(R.string.StickerManagement_search_no_results_s, searchQuery))
  } else if (packs.isEmpty()) {
    EmptyView(
      text = stringResource(R.string.StickerManagement_installed_tab_empty_text),
      actionButton = {
        Buttons.Small(onClick = onAddStickersClick) {
          Text(
            text = stringResource(id = R.string.StickerManagement__add_stickers),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    )
  } else {
    val listState = rememberLazyListState()
    val reorderableListState = rememberReorderableListState(lazyListState = listState, includeHeader = true, includeFooter = false, onEvent = callbacks::onReorderableEvent)

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
      var bottomActionBarPadding: Dp by remember { mutableStateOf(0.dp) }

      LazyColumn(
        contentPadding = PaddingValues(
          top = 8.dp,
          bottom = if (multiSelectEnabled) bottomActionBarPadding else 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        state = listState,
        modifier = if (searchQuery == null) {
          modifier
            .fillMaxHeight()
            .reorderableList(
              reorderableListState = reorderableListState,
              dragHandleWidth = 56.dp
            )
        } else {
          modifier.fillMaxWidth()
        }
      ) {
        item(key = "installed_section_header") {
          ReorderableItem(reorderableListState, 0) {
            StickerPackSectionHeader(
              text = stringResource(R.string.StickerManagement_installed_stickers_header),
              modifier = Modifier.animateItem()
            )
          }
        }

        itemsIndexed(
          items = packs,
          key = { _, pack -> pack.id.value }
        ) { index, pack ->
          val menuController = remember { DropdownMenus.MenuController() }

          ReorderableItem(
            index = index + 1,
            reorderableListState = reorderableListState
          ) { isDragging ->
            InstalledStickerPackRow(
              pack = pack,
              multiSelectEnabled = multiSelectEnabled,
              selected = pack.id in selectedPackIds,
              showDragHandle = searchQuery == null,
              menuController = menuController,
              onForwardClick = { callbacks.onForwardClick(pack) },
              onCopyClick = { callbacks.onCopyClick(pack) },
              onSelectionToggle = { callbacks.onSelectionToggle(pack) },
              onRemoveClick = { callbacks.onRemoveClick(setOf(pack.id)) },
              modifier = Modifier
                .shadow(if (isDragging) 1.dp else 0.dp)
                .combinedClickable(
                  onClick = {
                    if (multiSelectEnabled) {
                      callbacks.onSelectionToggle(pack)
                    } else {
                      callbacks.onShowPreviewClick(pack)
                    }
                  },
                  onLongClick = {
                    if (!multiSelectEnabled) {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      menuController.show()
                    }
                  },
                  onLongClickLabel = stringResource(R.string.StickerManagement_accessibility_open_context_menu)
                )
            )
          }
        }
      }

      SignalBottomActionBar(
        visible = multiSelectEnabled,
        items = listOf(
          ActionItem(
            iconRes = CoreUiR.drawable.symbol_check_circle_24,
            title = if (selectedPackIds.size == packs.size) {
              stringResource(R.string.StickerManagement_action_deselect_all)
            } else {
              stringResource(R.string.StickerManagement_action_select_all)
            },
            action = callbacks::onSelectAllToggle
          ),
          ActionItem(
            iconRes = CoreUiR.drawable.symbol_trash_24,
            title = stringResource(R.string.StickerManagement_action_delete_selected),
            action = { callbacks.onRemoveClick(selectedPackIds) }
          )
        ),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .onGloballyPositioned { layoutCoordinates ->
            bottomActionBarPadding = with(density) { layoutCoordinates.size.height.toDp() }
          }
      )
    }
  }
}

@Composable
private fun SnackbarHost(
  actionConfirmation: StickerManagementConfirmation?,
  onSnackbarDismiss: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val hostState = remember { SnackbarHostState() }

  val snackbarMessage = when (actionConfirmation) {
    is StickerManagementConfirmation.InstalledPack -> stringResource(R.string.StickerManagement_installed_pack_s, actionConfirmation.packTitle)

    is StickerManagementConfirmation.UninstalledPack -> stringResource(R.string.StickerManagement_deleted_pack_s, actionConfirmation.packTitle)

    is StickerManagementConfirmation.UninstalledPacks -> pluralStringResource(
      R.plurals.StickerManagement_deleted_n_packs,
      actionConfirmation.numPacksUninstalled,
      NumberFormat.getInstance().format(actionConfirmation.numPacksUninstalled)
    )

    is StickerManagementConfirmation.CopiedPack -> stringResource(R.string.StickerManagement_copied)
    null -> null
  }

  LaunchedEffect(actionConfirmation) {
    if (snackbarMessage != null) {
      val result = hostState.showSnackbar(
        message = snackbarMessage,
        duration = Snackbars.Duration.SHORT,
        withDismissAction = false
      )

      if (result == SnackbarResult.Dismissed) {
        onSnackbarDismiss()
      }
    }
  }

  Snackbars.Host(hostState, modifier = modifier)
}

@Composable
private fun EmptyView(
  text: String,
  actionButton: @Composable () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .wrapContentHeight(align = Alignment.CenterVertically)
      .horizontalGutters(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    actionButton()
  }
}

@DayNightPreviews
@Composable
private fun StickerManagementScreenEmptyStatePreview() {
  Previews.Preview {
    StickerManagementScreen(
      StickerManagementUiState()
    )
  }
}

@DayNightPreviews
@Composable
private fun AllStickersContentPreview() {
  Previews.Preview {
    AllStickersContent(
      blessedPacks = listOf(
        StickerPreviewDataFactory.stickerPack(
          title = "Swoon / Faces",
          author = "Swoon",
          isBlessed = true
        )
      ),
      notBlessedPacks = listOf(
        StickerPreviewDataFactory.stickerPack(
          title = "Bandit the Cat",
          author = "Agnes Lee",
          isBlessed = false,
          downloadStatus = DownloadStatus.InProgress
        ),
        StickerPreviewDataFactory.stickerPack(
          title = "Day by Day",
          author = "Miguel Ángel Camprubí",
          isBlessed = false,
          downloadStatus = DownloadStatus.Downloaded
        )
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun InstalledStickersContentPreview() {
  Previews.Preview {
    InstalledStickersContent(
      packs = listOf(
        StickerPreviewDataFactory.installedPack(
          title = "Swoon / Faces",
          author = "Swoon",
          isBlessed = true
        ),
        StickerPreviewDataFactory.installedPack(
          packId = "stickerPackId2",
          title = "Bandit the Cat",
          author = "Agnes Lee",
          isBlessed = true
        ),
        StickerPreviewDataFactory.installedPack(
          title = "Day by Day",
          author = "Miguel Ángel Camprubí"
        )
      ),
      multiSelectEnabled = true,
      selectedPackIds = setOf(StickerPackId("stickerPackId2"))
    )
  }
}

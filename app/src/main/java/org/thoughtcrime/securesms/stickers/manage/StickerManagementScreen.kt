/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.manage

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.copied.androidx.compose.DragAndDropEvent
import org.signal.core.ui.compose.copied.androidx.compose.DraggableItem
import org.signal.core.ui.compose.copied.androidx.compose.dragContainer
import org.signal.core.ui.compose.copied.androidx.compose.rememberDragDropState
import org.signal.core.ui.compose.showSnackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory
import org.thoughtcrime.securesms.stickers.manage.AvailableStickerPack.DownloadStatus
import org.thoughtcrime.securesms.window.getWindowSizeClass
import java.text.NumberFormat
import org.signal.core.ui.R as CoreUiR

object StickerManagementScreen {
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

interface AvailableStickersContentCallbacks {
  fun onForwardClick(pack: AvailableStickerPack)
  fun onInstallClick(pack: AvailableStickerPack)
  fun onShowPreviewClick(pack: AvailableStickerPack)

  object Empty : AvailableStickersContentCallbacks {
    override fun onForwardClick(pack: AvailableStickerPack) = Unit
    override fun onInstallClick(pack: AvailableStickerPack) = Unit
    override fun onShowPreviewClick(pack: AvailableStickerPack) = Unit
  }
}

interface InstalledStickersContentCallbacks {
  fun onForwardClick(pack: InstalledStickerPack)
  fun onRemoveClick(packIds: Set<StickerPackId>)
  fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>)
  fun onRemoveStickerPacksCanceled()
  fun onSelectionToggle(pack: InstalledStickerPack)
  fun onSelectAllToggle()
  fun onDragAndDropEvent(event: DragAndDropEvent)
  fun onShowPreviewClick(pack: InstalledStickerPack)

  object Empty : InstalledStickersContentCallbacks {
    override fun onForwardClick(pack: InstalledStickerPack) = Unit
    override fun onRemoveClick(packIds: Set<StickerPackId>) = Unit
    override fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>) = Unit
    override fun onRemoveStickerPacksCanceled() = Unit
    override fun onSelectionToggle(pack: InstalledStickerPack) = Unit
    override fun onSelectAllToggle() = Unit
    override fun onDragAndDropEvent(event: DragAndDropEvent) = Unit
    override fun onShowPreviewClick(pack: InstalledStickerPack) = Unit
  }
}

/**
 * Displays all the available and installed sticker packs, enabling installation, uninstallation, and sorting.
 *
 * @see StickerManagementActivity
 * @see StickerManagementBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManagementScreen(
  uiState: StickerManagementUiState,
  showNavigateBack: Boolean = true,
  onNavigateBack: () -> Unit = {},
  onSetMultiSelectModeEnabled: (Boolean) -> Unit = {},
  onSnackbarDismiss: () -> Unit = {},
  availableTabCallbacks: AvailableStickersContentCallbacks = AvailableStickersContentCallbacks.Empty,
  installedTabCallbacks: InstalledStickersContentCallbacks = InstalledStickersContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  val pages = listOf(
    Page(
      title = stringResource(R.string.StickerManagement_available_tab_label),
      getContent = {
        AvailableStickersContent(
          blessedPacks = uiState.availableBlessedPacks,
          notBlessedPacks = uiState.availableNotBlessedPacks,
          callbacks = availableTabCallbacks
        )
      }
    ),
    Page(
      title = stringResource(R.string.StickerManagement_installed_tab_label),
      getContent = {
        InstalledStickersContent(
          packs = uiState.installedPacks,
          multiSelectEnabled = uiState.multiSelectEnabled,
          selectedPackIds = uiState.selectedPackIds,
          dialogState = uiState.userPrompt,
          callbacks = installedTabCallbacks
        )
      }
    )
  )

  val pagerState = rememberPagerState(pageCount = { pages.size })
  val coroutineScope = rememberCoroutineScope()

  BackHandler(enabled = uiState.multiSelectEnabled) {
    onSetMultiSelectModeEnabled(false)
  }

  Scaffold(
    topBar = {
      val isInstalledTabActive = pagerState.currentPage == 1
      if (isInstalledTabActive && uiState.multiSelectEnabled) {
        MultiSelectTopAppBar(
          selectedItemCount = uiState.selectedPackIds.size,
          onExitClick = { onSetMultiSelectModeEnabled(false) }
        )
      } else {
        TopAppBar(
          showNavigateBack = showNavigateBack,
          onBackPress = onNavigateBack,
          showMenuButton = isInstalledTabActive,
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
private fun AvailableStickersContent(
  blessedPacks: List<AvailableStickerPack>,
  notBlessedPacks: List<AvailableStickerPack>,
  callbacks: AvailableStickersContentCallbacks = AvailableStickersContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  if (blessedPacks.isEmpty() && notBlessedPacks.isEmpty()) {
    EmptyView(text = stringResource(R.string.StickerManagement_available_tab_empty_text))
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
          items = blessedPacks,
          key = { it.id.value }
        ) { pack ->
          val menuController = remember { DropdownMenus.MenuController() }
          AvailableStickerPackRow(
            pack = pack,
            menuController = menuController,
            onForwardClick = callbacks::onForwardClick,
            onInstallClick = callbacks::onInstallClick,
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
      }

      if (blessedPacks.isNotEmpty() && notBlessedPacks.isNotEmpty()) {
        item { Dividers.Default() }
      }

      if (notBlessedPacks.isNotEmpty()) {
        item(key = "not_blessed_section_header") {
          StickerPackSectionHeader(
            text = stringResource(R.string.StickerManagement_stickers_you_received_header),
            modifier = Modifier.animateItem()
          )
        }
        items(
          items = notBlessedPacks,
          key = { it.id.value }
        ) { pack ->
          val menuController = remember { DropdownMenus.MenuController() }
          AvailableStickerPackRow(
            pack = pack,
            menuController = menuController,
            onForwardClick = callbacks::onForwardClick,
            onInstallClick = callbacks::onInstallClick,
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
  packs: List<InstalledStickerPack>,
  multiSelectEnabled: Boolean,
  selectedPackIds: Set<StickerPackId>,
  dialogState: ConfirmRemoveStickerPacksPrompt? = null,
  callbacks: InstalledStickersContentCallbacks = InstalledStickersContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  if (packs.isEmpty()) {
    EmptyView(text = stringResource(R.string.StickerManagement_installed_tab_empty_text))
  } else {
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState = listState, includeHeader = true, includeFooter = false, onEvent = callbacks::onDragAndDropEvent)

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
        modifier = modifier
          .fillMaxHeight()
          .dragContainer(
            dragDropState = dragDropState,
            dragHandleWidth = 56.dp
          )
      ) {
        item(key = "installed_section_header") {
          DraggableItem(dragDropState, 0) {
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

          DraggableItem(
            index = index + 1,
            dragDropState = dragDropState
          ) { isDragging ->
            InstalledStickerPackRow(
              pack = pack,
              multiSelectEnabled = multiSelectEnabled,
              selected = pack.id in selectedPackIds,
              menuController = menuController,
              onForwardClick = { callbacks.onForwardClick(pack) },
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

      if (dialogState != null) {
        Dialogs.SimpleAlertDialog(
          title = Dialogs.NoTitle,
          body = pluralStringResource(
            R.plurals.StickerManagement_delete_n_packs_confirmation,
            dialogState.numItemsToDelete,
            NumberFormat.getInstance().format(dialogState.numItemsToDelete)
          ),
          confirm = stringResource(R.string.StickerManagement_menu_remove_pack),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = { callbacks.onRemoveStickerPacksConfirmed(selectedPackIds) },
          onDeny = callbacks::onRemoveStickerPacksCanceled,
          onDismiss = callbacks::onRemoveStickerPacksCanceled
        )
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
  text: String
) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxSize()
      .wrapContentHeight(align = Alignment.CenterVertically)
  )
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
private fun AvailableStickersContentPreview() {
  Previews.Preview {
    AvailableStickersContent(
      blessedPacks = listOf(
        StickerPreviewDataFactory.availablePack(
          title = "Swoon / Faces",
          author = "Swoon",
          isBlessed = true
        )
      ),
      notBlessedPacks = listOf(
        StickerPreviewDataFactory.availablePack(
          title = "Bandit the Cat",
          author = "Agnes Lee",
          isBlessed = false,
          downloadStatus = DownloadStatus.InProgress
        ),
        StickerPreviewDataFactory.availablePack(
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

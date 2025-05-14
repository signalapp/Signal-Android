/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.copied.androidx.compose.DragAndDropEvent
import org.signal.core.ui.compose.copied.androidx.compose.DraggableItem
import org.signal.core.ui.compose.copied.androidx.compose.dragContainer
import org.signal.core.ui.compose.copied.androidx.compose.rememberDragDropState
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus
import org.thoughtcrime.securesms.util.viewModel
import java.text.NumberFormat

/**
 * Displays all of the available and installed sticker packs, enabling installation, uninstallation, and sorting.
 */
class StickerManagementActivity : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, StickerManagementActivity::class.java)
  }

  private val viewModel by viewModel { StickerManagementViewModel() }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.onScreenVisible()
      }
    }

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      SignalTheme {
        StickerManagementScreen(
          uiState = uiState,
          onNavigateBack = ::supportFinishAfterTransition,
          onSetMultiSelectModeEnabled = viewModel::setMultiSelectEnabled,
          onSnackbarDismiss = { viewModel.onSnackbarDismiss() },
          availableTabCallbacks = object : AvailableStickersContentCallbacks {
            override fun onForwardClick(pack: AvailableStickerPack) = openShareSheet(pack.id, pack.key)
            override fun onInstallClick(pack: AvailableStickerPack) = viewModel.installStickerPack(pack)
            override fun onShowPreviewClick(pack: AvailableStickerPack) = navigateToStickerPreview(pack.id, pack.key)
          },
          installedTabCallbacks = object : InstalledStickersContentCallbacks {
            override fun onForwardClick(pack: InstalledStickerPack) = openShareSheet(pack.id, pack.key)
            override fun onRemoveClick(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksRequested(packIds)
            override fun onRemoveStickerPacksConfirmed(packIds: Set<StickerPackId>) = viewModel.onUninstallStickerPacksConfirmed(packIds)
            override fun onRemoveStickerPacksCanceled() = viewModel.onUninstallStickerPacksCanceled()
            override fun onSelectionToggle(pack: InstalledStickerPack) = viewModel.toggleSelection(pack)
            override fun onSelectAllToggle() = viewModel.toggleSelectAll()
            override fun onDragAndDropEvent(event: DragAndDropEvent) {
              when (event) {
                is DragAndDropEvent.OnItemMove -> viewModel.updatePosition(event.fromIndex, event.toIndex)
                is DragAndDropEvent.OnItemDrop -> viewModel.saveInstalledPacksSortOrder()
                is DragAndDropEvent.OnDragCancel -> {}
              }
            }

            override fun onShowPreviewClick(pack: InstalledStickerPack) = navigateToStickerPreview(pack.id, pack.key)
          }
        )
      }
    }
  }

  private fun openShareSheet(packId: StickerPackId, packKey: StickerPackKey) {
    MultiselectForwardFragment.showBottomSheet(
      supportFragmentManager = supportFragmentManager,
      multiselectForwardFragmentArgs = MultiselectForwardFragmentArgs(
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDraftText(StickerUrl.createShareLink(packId.value, packKey.value))
            .build()
        ),
        title = R.string.StickerManagement_share_sheet_title
      )
    )
  }

  private fun navigateToStickerPreview(packId: StickerPackId, packKey: StickerPackKey) {
    startActivity(StickerPackPreviewActivity.getIntent(packId.value, packKey.value))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerManagementScreen(
  uiState: StickerManagementUiState,
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
  showMenuButton: Boolean = false,
  onBackPress: () -> Unit,
  onSetMultiSelectModeEnabled: (Boolean) -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = stringResource(R.string.StickerManagement_title_stickers),
    titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
    onNavigationClick = onBackPress,
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
          offsetY = 0.dp,
          modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(min = 200.dp)
            .background(SignalTheme.colors.colorSurface2)
        ) {
          DropdownMenus.Item(
            text = {
              Text(
                text = stringResource(R.string.StickerManagement_menu_select_packs),
                style = MaterialTheme.typography.bodyLarge
              )
            },
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
    navigationIconPainter = painterResource(R.drawable.symbol_x_24),
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

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
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
            leftDpOffset = if (isRtl) 0.dp else screenWidth - 56.dp,
            rightDpOffset = if (isRtl) 56.dp else screenWidth
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
            iconRes = R.drawable.symbol_check_circle_24,
            title = if (selectedPackIds.size == packs.size) {
              stringResource(R.string.StickerManagement_action_deselect_all)
            } else {
              stringResource(R.string.StickerManagement_action_select_all)
            },
            action = callbacks::onSelectAllToggle
          ),
          ActionItem(
            iconRes = R.drawable.symbol_trash_24,
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
        duration = SnackbarDuration.Short,
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

@SignalPreview
@Composable
private fun StickerManagementScreenEmptyStatePreview() {
  Previews.Preview {
    StickerManagementScreen(
      StickerManagementUiState()
    )
  }
}

@SignalPreview
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

@SignalPreview
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

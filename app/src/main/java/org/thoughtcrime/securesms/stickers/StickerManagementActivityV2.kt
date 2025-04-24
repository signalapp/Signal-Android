/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.copied.androidx.compose.DragAndDropEvent
import org.signal.core.ui.compose.copied.androidx.compose.DraggableItem
import org.signal.core.ui.compose.copied.androidx.compose.dragContainer
import org.signal.core.ui.compose.copied.androidx.compose.rememberDragDropState
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displays all of the available and installed sticker packs, enabling installation, uninstallation, and sorting.
 */
class StickerManagementActivityV2 : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, StickerManagementActivityV2::class.java)
  }

  private val viewModel by viewModel { StickerManagementViewModelV2() }

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
          availableTabCallbacks = object : AvailableStickersContentCallbacks {
            override fun onInstallClick(pack: AvailableStickerPack) = viewModel.installStickerPack(pack)
          },
          installedTabCallbacks = object : InstalledStickersContentCallbacks {
            override fun onDragAndDropEvent(event: DragAndDropEvent) {
              when (event) {
                is DragAndDropEvent.OnItemMove -> viewModel.updatePosition(event.fromIndex, event.toIndex)
                is DragAndDropEvent.OnItemDrop -> viewModel.saveInstalledPacksSortOrder()
                is DragAndDropEvent.OnDragCancel -> {}
              }
            }
          }
        )
      }
    }
  }
}

private data class Page(
  val title: String,
  val getContent: @Composable () -> Unit
)

interface AvailableStickersContentCallbacks {
  fun onInstallClick(pack: AvailableStickerPack)

  object Empty : AvailableStickersContentCallbacks {
    override fun onInstallClick(pack: AvailableStickerPack) = Unit
  }
}

interface InstalledStickersContentCallbacks {
  fun onDragAndDropEvent(event: DragAndDropEvent)

  object Empty : InstalledStickersContentCallbacks {
    override fun onDragAndDropEvent(event: DragAndDropEvent) = Unit
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerManagementScreen(
  uiState: StickerManagementUiState,
  onNavigateBack: () -> Unit = {},
  availableTabCallbacks: AvailableStickersContentCallbacks = AvailableStickersContentCallbacks.Empty,
  installedTabCallbacks: InstalledStickersContentCallbacks = InstalledStickersContentCallbacks.Empty,
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = { TopAppBar(onBackPress = onNavigateBack) }
  ) { padding ->

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
            callbacks = installedTabCallbacks
          )
        }
      )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

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
  onBackPress: () -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = stringResource(R.string.StickerManagementActivity_stickers),
    titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
    navigationIconPainter = painterResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onBackPress
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
    LazyColumn(
      contentPadding = PaddingValues(top = 8.dp),
      modifier = modifier.fillMaxHeight()
    ) {
      if (blessedPacks.isNotEmpty()) {
        item { StickerPackSectionHeader(text = stringResource(R.string.StickerManagement_signal_artist_series_header)) }
        items(
          items = blessedPacks,
          key = { it.id.value }
        ) {
          AvailableStickerPackRow(
            pack = it,
            onInstallClick = { callbacks.onInstallClick(it) },
            modifier = Modifier.animateItem()
          )
        }
      }

      if (blessedPacks.isNotEmpty() && notBlessedPacks.isNotEmpty()) {
        item { Dividers.Default() }
      }

      if (notBlessedPacks.isNotEmpty()) {
        item { StickerPackSectionHeader(text = stringResource(R.string.StickerManagement_stickers_you_received_header)) }
        items(
          items = notBlessedPacks,
          key = { it.id.value }
        ) {
          AvailableStickerPackRow(
            pack = it,
            onInstallClick = { callbacks.onInstallClick(it) },
            modifier = Modifier.animateItem()
          )
        }
      }
    }
  }
}

@Composable
private fun InstalledStickersContent(
  packs: List<InstalledStickerPack>,
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

    LazyColumn(
      contentPadding = PaddingValues(top = 8.dp),
      state = listState,
      modifier = modifier
        .fillMaxHeight()
        .dragContainer(
          dragDropState = dragDropState,
          leftDpOffset = if (isRtl) 0.dp else screenWidth - 56.dp,
          rightDpOffset = if (isRtl) 56.dp else screenWidth
        )
    ) {
      item {
        DraggableItem(dragDropState, 0) {
          StickerPackSectionHeader(text = stringResource(R.string.StickerManagement_installed_stickers_header))
        }
      }

      itemsIndexed(
        items = packs,
        key = { _, pack -> pack.id.value }
      ) { index, pack ->
        DraggableItem(
          index = index + 1,
          dragDropState = dragDropState
        ) { isDragging ->
          InstalledStickerPackRow(
            pack = pack,
            modifier = Modifier.shadow(if (isDragging) 1.dp else 0.dp)
          )
        }
      }
    }
  }
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
          title = "Bandit the Cat",
          author = "Agnes Lee",
          isBlessed = true
        ),
        StickerPreviewDataFactory.installedPack(
          title = "Day by Day",
          author = "Miguel Ángel Camprubí"
        )
      )
    )
  }
}

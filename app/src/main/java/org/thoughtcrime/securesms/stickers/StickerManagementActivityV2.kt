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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
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

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      SignalTheme {
        StickerManagementScreen(
          uiState = uiState,
          onNavigateBack = ::supportFinishAfterTransition
        )
      }
    }
  }
}

private data class Page(
  val title: String,
  val getContent: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerManagementScreen(
  uiState: StickerManagementUiState,
  onNavigateBack: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Scaffold(
    topBar = { TopAppBar(onBackPress = onNavigateBack) }
  ) { padding ->

    val pages = listOf(
      Page(
        title = stringResource(R.string.StickerManagement_available_tab_label),
        getContent = { AvailableStickersContent(blessedPacks = uiState.availableBlessedPacks, availablePacks = uiState.availablePacks) }
      ),
      Page(
        title = stringResource(R.string.StickerManagement_installed_tab_label),
        getContent = { InstalledStickersContent(uiState.installedPacks) }
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
  availablePacks: List<AvailableStickerPack>,
  modifier: Modifier = Modifier
) {
  if (blessedPacks.isEmpty() && availablePacks.isEmpty()) {
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
          key = { it.record.packId }
        ) {
          AvailableStickerPackRow(it)
        }
      }

      if (blessedPacks.isNotEmpty() && availablePacks.isNotEmpty()) {
        item { Dividers.Default() }
      }

      if (availablePacks.isNotEmpty()) {
        item { StickerPackSectionHeader(text = stringResource(R.string.StickerManagement_stickers_you_received_header)) }
        items(
          items = availablePacks,
          key = { it.record.packId }
        ) {
          AvailableStickerPackRow(it)
        }
      }
    }
  }
}

@Composable
private fun InstalledStickersContent(
  packs: List<InstalledStickerPack>,
  modifier: Modifier = Modifier
) {
  if (packs.isEmpty()) {
    EmptyView(text = stringResource(R.string.StickerManagement_installed_tab_empty_text))
  } else {
    LazyColumn(
      contentPadding = PaddingValues(top = 8.dp),
      modifier = modifier.fillMaxHeight()
    ) {
      item { StickerPackSectionHeader(text = stringResource(R.string.StickerManagement_installed_stickers_header)) }
      items(
        items = packs,
        key = { it.record.packId }
      ) {
        InstalledStickerPackRow(it)
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
      StickerManagementUiState(
        availablePacks = emptyList(),
        installedPacks = emptyList()
      )
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
      availablePacks = listOf(
        StickerPreviewDataFactory.availablePack(
          title = "Bandit the Cat",
          author = "Agnes Lee",
          isBlessed = false,
          downloadStatus = DownloadStatus.InProgress(progressPercent = 22.0)
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

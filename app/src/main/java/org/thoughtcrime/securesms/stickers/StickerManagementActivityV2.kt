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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.viewModel

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
        getContent = { AvailableStickersContent(uiState.availablePacks) }
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
  packs: List<AvailableStickerPack>
) {
  if (packs.isEmpty()) {
    EmptyView(text = stringResource(R.string.StickerManagement_available_tab_empty_text))
  } else {
    // TODO show available stickers list
  }
}

@Composable
private fun InstalledStickersContent(
  packs: List<InstalledStickerPack>
) {
  if (packs.isEmpty()) {
    EmptyView(text = stringResource(R.string.StickerManagement_installed_tab_empty_text))
  } else {
    // TODO show installed stickers list
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

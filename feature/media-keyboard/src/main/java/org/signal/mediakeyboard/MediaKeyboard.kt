/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.ui.compose.keyboard.LocalKeyboardSheetController
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.signal.mediakeyboard.screens.LocalMediaKeyboardExpanded
import org.signal.mediakeyboard.screens.emoji.EmojiPageScreen
import org.signal.mediakeyboard.screens.emoji.EmojiPageViewModel
import org.signal.mediakeyboard.screens.gif.GifPageScreen
import org.signal.mediakeyboard.screens.gif.GifPageViewModel
import org.signal.mediakeyboard.screens.sticker.StickerPageScreen
import org.signal.mediakeyboard.screens.sticker.StickerPageViewModel

/**
 * The emoji/sticker/gif media keyboard.
 *
 * Fills whatever space its host hands it, so the host owns the surface, its window insets, and any
 * drag handle. Search needs more room than a keyboard-sized space and a system keyboard to type the
 * query into, so it takes text entry over from the scaffold via
 * [org.signal.core.ui.compose.keyboard.KeyboardSheetController]; the scaffold is then the one
 * deciding the height and the system keyboard together.
 *
 * Layout: a top bar with search, text tabs, and a per-tab action, then the page content, with each
 * page providing its own bottom rail (emoji categories, sticker packs, gif quick searches).
 *
 * @param repository Supplies the emoji, sticker, and gif data.
 * @param onAction Receives what the user picks, and anything only a host can carry out.
 * @param tabs The only tabs to offer, or null to offer everything [repository] has. Hosts that
 *   cannot accept every kind of media, such as a conversation editing a message, narrow this.
 * @param initialTab The tab to open on. A host that recorded [MediaKeyboardAction.TabSelected]
 *   passes back what it recorded; null opens on the first tab offered.
 */
@Composable
fun MediaKeyboard(
  repository: MediaKeyboardRepository,
  onAction: (MediaKeyboardAction) -> Unit,
  modifier: Modifier = Modifier,
  tabs: Set<MediaKeyboardTab>? = null,
  initialTab: MediaKeyboardTab? = null
) {
  val host = LocalKeyboardSheetController.current
  val viewModel: MediaKeyboardViewModel = viewModel(factory = MediaKeyboardViewModel.Factory(repository, host.isEnteringText, initialTab))
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(tabs) {
    viewModel.restrictTabs(tabs)
  }

  val searching = state.searchActive

  LaunchedEffect(searching) {
    if (searching) {
      viewModel.onEvent(MediaKeyboardScreenEvents.SearchQueryChanged(""))
    }
  }

  BackHandler(enabled = searching) {
    host.endTextEntry()
  }

  // Two search affordances at once is one too many, so the icon goes while a page's own field shows.
  var searchFieldRevealed by remember { mutableStateOf(false) }

  // Reset on a tab change so a page without a field of its own never inherits the last page's answer.
  LaunchedEffect(state.selectedTab) {
    searchFieldRevealed = false
  }

  CompositionLocalProvider(LocalMediaKeyboardExpanded provides host.isExpanded) {
    Column(
      modifier = modifier
        .fillMaxSize()
        // The system keyboard the search field summons sits on top of us rather than beside us.
        .then(if (searching) Modifier.imePadding() else Modifier)
    ) {
      if (searching) {
        MediaKeyboardSearchBar(state = state, onEvent = viewModel::onEvent, onCloseSearch = host::endTextEntry)
      } else {
        MediaKeyboardTopBar(
          state = state,
          onEvent = viewModel::onEvent,
          onAction = onAction,
          onOpenSearch = host::beginTextEntry,
          searchFieldRevealed = searchFieldRevealed
        )
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (state.initialized) {
          when (state.selectedTab) {
            MediaKeyboardTab.EMOJI -> EmojiTab(repository, viewModel.state, onAction)
            MediaKeyboardTab.STICKER -> StickerTab(
              repository = repository,
              onAction = onAction,
              onSearchFieldRevealedChange = { searchFieldRevealed = it }
            )
            MediaKeyboardTab.GIF -> GifTab(
              repository = repository,
              onAction = onAction,
              onSearchFieldRevealedChange = { searchFieldRevealed = it }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MediaKeyboardTopBar(
  state: MediaKeyboardState,
  onEvent: (MediaKeyboardScreenEvents) -> Unit,
  onAction: (MediaKeyboardAction) -> Unit,
  onOpenSearch: () -> Unit,
  searchFieldRevealed: Boolean
) {
  // A null action means searching in place, which emoji is now the only page to do.
  val searchAction = when (state.selectedTab) {
    MediaKeyboardTab.EMOJI -> null
    MediaKeyboardTab.STICKER -> MediaKeyboardAction.StickerSearchClicked
    MediaKeyboardTab.GIF -> MediaKeyboardAction.GifSearchClicked
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp)
  ) {
    // Kept in the layout so it fades in place and the tabs never shift.
    val searchAlpha by animateFloatAsState(
      targetValue = if (searchFieldRevealed) 0f else 1f,
      label = "searchAlpha"
    )

    IconButton(
      onClick = {
        if (searchAction != null) {
          onAction(searchAction)
        } else {
          onOpenSearch()
        }
      },
      enabled = searchAlpha > 0f,
      modifier = Modifier.graphicsLayer { alpha = searchAlpha }
    ) {
      Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = stringResource(R.string.MediaKeyboard__search),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.weight(1f)
    ) {
      if (state.availableTabs.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          state.availableTabs.forEach { tab ->
            TabPill(
              tab = tab,
              selected = tab == state.selectedTab,
              onClick = {
                onEvent(MediaKeyboardScreenEvents.TabSelected(tab))
                onAction(MediaKeyboardAction.TabSelected(tab))
              }
            )
          }
        }
      }
    }

    when (state.selectedTab) {
      MediaKeyboardTab.EMOJI -> {
        IconButton(onClick = { onAction(MediaKeyboardAction.Backspace) }) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = stringResource(R.string.MediaKeyboard__backspace),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      MediaKeyboardTab.STICKER -> {
        IconButton(onClick = { onAction(MediaKeyboardAction.StickerManagementClicked) }) {
          Icon(
            imageVector = Icons.Outlined.AddCircleOutline,
            contentDescription = stringResource(R.string.MediaKeyboard__manage_stickers),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      MediaKeyboardTab.GIF -> {
        Box(modifier = Modifier.size(48.dp))
      }
    }
  }
}

@Composable
private fun TabPill(
  tab: MediaKeyboardTab,
  selected: Boolean,
  onClick: () -> Unit
) {
  val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
  val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

  Text(
    text = stringResource(tab.label()),
    style = MaterialTheme.typography.labelLarge,
    color = textColor,
    modifier = Modifier
      .clip(CircleShape)
      .background(background)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 8.dp)
  )
}

@Composable
private fun EmojiTab(
  repository: MediaKeyboardRepository,
  parentStateFlow: StateFlow<MediaKeyboardState>,
  onAction: (MediaKeyboardAction) -> Unit
) {
  val viewModel: EmojiPageViewModel = viewModel(
    key = "media-keyboard-emoji",
    factory = EmojiPageViewModel.Factory(repository.emoji, parentStateFlow, onAction)
  )
  val state by viewModel.state.collectAsStateWithLifecycle()

  EmojiPageScreen(
    state = state,
    onEvent = viewModel::onEvent,
    getEmojiDrawable = repository.emoji::getEmojiDrawable
  )
}

@Composable
private fun StickerTab(
  repository: MediaKeyboardRepository,
  onAction: (MediaKeyboardAction) -> Unit,
  onSearchFieldRevealedChange: (Boolean) -> Unit
) {
  val viewModel: StickerPageViewModel = viewModel(
    key = "media-keyboard-sticker",
    factory = StickerPageViewModel.Factory(repository.stickers, onAction)
  )
  val state by viewModel.state.collectAsStateWithLifecycle()

  StickerPageScreen(
    state = state,
    onEvent = viewModel::onEvent,
    onSearchFieldRevealedChange = onSearchFieldRevealedChange
  )
}

@Composable
private fun GifTab(
  repository: MediaKeyboardRepository,
  onAction: (MediaKeyboardAction) -> Unit,
  onSearchFieldRevealedChange: (Boolean) -> Unit
) {
  val viewModel: GifPageViewModel = viewModel(
    key = "media-keyboard-gif",
    factory = GifPageViewModel.Factory(repository.gifs, onAction)
  )
  val state by viewModel.state.collectAsStateWithLifecycle()

  GifPageScreen(
    state = state,
    onEvent = viewModel::onEvent,
    onSearchFieldRevealedChange = onSearchFieldRevealedChange
  )
}

@Composable
private fun MediaKeyboardSearchBar(
  state: MediaKeyboardState,
  onEvent: (MediaKeyboardScreenEvents) -> Unit,
  onCloseSearch: () -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    IconButton(onClick = onCloseSearch) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.MediaKeyboard__close_search),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    TextField(
      value = state.searchQuery,
      onValueChange = { onEvent(MediaKeyboardScreenEvents.SearchQueryChanged(it)) },
      placeholder = { Text(text = stringResource(state.selectedTab.searchHint())) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      trailingIcon = {
        if (state.searchQuery.isNotEmpty()) {
          IconButton(onClick = { onEvent(MediaKeyboardScreenEvents.SearchQueryChanged("")) }) {
            Icon(
              imageVector = Icons.Outlined.Clear,
              contentDescription = stringResource(R.string.MediaKeyboard__clear_search),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      },
      singleLine = true,
      shape = CircleShape,
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
      ),
      modifier = Modifier
        .weight(1f)
        .focusRequester(focusRequester)
    )
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@StringRes
private fun MediaKeyboardTab.label(): Int {
  return when (this) {
    MediaKeyboardTab.EMOJI -> R.string.MediaKeyboard__emoji
    MediaKeyboardTab.STICKER -> R.string.MediaKeyboard__stickers
    MediaKeyboardTab.GIF -> R.string.MediaKeyboard__gifs
  }
}

@StringRes
private fun MediaKeyboardTab.searchHint(): Int {
  return when (this) {
    MediaKeyboardTab.EMOJI -> R.string.MediaKeyboard__search_emoji
    MediaKeyboardTab.STICKER -> R.string.MediaKeyboard__search_stickers
    MediaKeyboardTab.GIF -> R.string.MediaKeyboard__search_gifs
  }
}

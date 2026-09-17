/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiFlags
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.EmojiNature
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.EmojiTransportation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.GRID_CONTENT_PADDING
import org.signal.mediakeyboard.screens.PinnedRailLayout

@Composable
fun EmojiPageScreen(
  state: EmojiPageState,
  onEvent: (EmojiPageScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  getEmojiDrawable: (String) -> Drawable? = { null }
) {
  val searching = state.searchResults != null

  PinnedRailLayout(
    rail = if (searching) {
      null
    } else {
      { EmojiCategoryRail(state = state, onEvent = onEvent) }
    },
    modifier = modifier
  ) {
    if (searching) {
      EmojiSearchResults(
        state = state,
        onEvent = onEvent,
        getEmojiDrawable = getEmojiDrawable
      )
    } else {
      EmojiGrid(
        state = state,
        onEvent = onEvent,
        getEmojiDrawable = getEmojiDrawable
      )
    }
  }
}

@Composable
private fun EmojiCategoryRail(
  state: EmojiPageState,
  onEvent: (EmojiPageScreenEvents) -> Unit
) {
  Surface(color = SignalTheme.colors.colorSurface5) {
    LazyRow(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
      items(state.pages, key = { it.category.key }) { page ->
        CategoryButton(
          category = page.category,
          selected = page.category == state.selectedCategory,
          onClick = { onEvent(EmojiPageScreenEvents.CategorySelected(page.category)) }
        )
      }
    }
  }
}

@Composable
private fun CategoryButton(
  category: EmojiKeyboardCategory,
  selected: Boolean,
  onClick: () -> Unit
) {
  IconButton(onClick = onClick) {
    Icon(
      imageVector = category.icon(),
      contentDescription = stringResource(category.label),
      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = if (selected) {
        Modifier
          .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
          .padding(6.dp)
      } else {
        Modifier
      }
    )
  }
}

@Composable
private fun EmojiGrid(
  state: EmojiPageState,
  onEvent: (EmojiPageScreenEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?,
  modifier: Modifier = Modifier
) {
  val gridState = rememberLazyGridState()

  val headerIndices = remember(state.pages) {
    var index = 0
    buildMap {
      state.pages.forEach { page ->
        put(page.category, index)
        index += 1 + page.emoji.size
      }
    }
  }

  // Start scrolled just past the first section header so more content is visible; scrolling up
  // still reveals it.
  LaunchedEffect(state.pages.isNotEmpty()) {
    if (state.pages.isNotEmpty()) {
      gridState.scrollToItem(1)
    }
  }

  LaunchedEffect(state.scrollTarget) {
    val target = state.scrollTarget ?: return@LaunchedEffect
    headerIndices[target]?.let { gridState.scrollToItem(it) }
    onEvent(EmojiPageScreenEvents.ScrollTargetConsumed)
  }

  val visibleCategory by remember(state.pages, headerIndices) {
    derivedStateOf {
      val firstVisible = gridState.firstVisibleItemIndex
      state.pages
        .lastOrNull { page -> (headerIndices[page.category] ?: Int.MAX_VALUE) <= firstVisible }
        ?.category
    }
  }

  LaunchedEffect(visibleCategory) {
    visibleCategory?.let { onEvent(EmojiPageScreenEvents.VisibleCategoryChanged(it)) }
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 44.dp),
    state = gridState,
    contentPadding = GRID_CONTENT_PADDING,
    modifier = modifier.fillMaxWidth()
  ) {
    state.pages.forEach { page ->
      item(key = "header:${page.category.key}", span = { GridItemSpan(maxLineSpan) }) {
        Text(
          text = stringResource(page.category.label),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
        )
      }

      page.emoji.forEachIndexed { index, emoji ->
        val cellKey = "${page.category.key}:$index"
        item(key = cellKey) {
          EmojiCell(
            emoji = emoji,
            display = state.displayEmoji(emoji),
            cellKey = cellKey,
            showVariationSelector = state.variationSelector?.cellKey == cellKey,
            onEvent = onEvent,
            getEmojiDrawable = getEmojiDrawable,
            modifier = Modifier.aspectRatio(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun EmojiSearchResults(
  state: EmojiPageState,
  onEvent: (EmojiPageScreenEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?
) {
  val results = state.searchResults ?: return

  if (results.isEmpty()) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize()
    ) {
      if (state.searchQuery.isNotBlank()) {
        Text(
          text = stringResource(R.string.MediaKeyboard__no_results_found),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  } else {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 44.dp),
      contentPadding = GRID_CONTENT_PADDING,
      modifier = Modifier.fillMaxSize()
    ) {
      results.forEachIndexed { index, emoji ->
        val cellKey = "search:$index"
        item(key = cellKey) {
          EmojiCell(
            emoji = emoji,
            display = state.displayEmoji(emoji),
            cellKey = cellKey,
            showVariationSelector = state.variationSelector?.cellKey == cellKey,
            onEvent = onEvent,
            getEmojiDrawable = getEmojiDrawable,
            modifier = Modifier.aspectRatio(1f)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
  emoji: KeyboardEmoji,
  display: String,
  cellKey: String,
  showVariationSelector: Boolean,
  onEvent: (EmojiPageScreenEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .clip(CircleShape)
      .combinedClickable(
        onClick = { onEvent(EmojiPageScreenEvents.EmojiClicked(emoji)) },
        onLongClick = { onEvent(EmojiPageScreenEvents.EmojiLongPressed(cellKey, emoji)) }
      )
  ) {
    EmojiImage(emoji = display, getEmojiDrawable = getEmojiDrawable)

    if (emoji.hasVariations) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(5.dp)
          .size(4.dp)
          .background(MaterialTheme.colorScheme.outline, CircleShape)
      )
    }

    if (showVariationSelector) {
      VariationSelectorPopup(
        emoji = emoji,
        onEvent = onEvent,
        getEmojiDrawable = getEmojiDrawable
      )
    }
  }
}

@Composable
private fun VariationSelectorPopup(
  emoji: KeyboardEmoji,
  onEvent: (EmojiPageScreenEvents) -> Unit,
  getEmojiDrawable: (String) -> Drawable?
) {
  val yOffset = with(LocalDensity.current) { -56.dp.roundToPx() }

  Popup(
    alignment = Alignment.TopCenter,
    offset = IntOffset(0, yOffset),
    onDismissRequest = { onEvent(EmojiPageScreenEvents.VariationSelectorDismissed) },
    properties = PopupProperties(focusable = true)
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shadowElevation = 4.dp
    ) {
      Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        emoji.variations.forEach { variation ->
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .clickable { onEvent(EmojiPageScreenEvents.VariationSelected(emoji, variation)) }
          ) {
            EmojiImage(emoji = variation, getEmojiDrawable = getEmojiDrawable)
          }
        }
      }
    }
  }
}

@Composable
private fun EmojiImage(
  emoji: String,
  getEmojiDrawable: (String) -> Drawable?,
  modifier: Modifier = Modifier
) {
  val drawable = remember(emoji) { getEmojiDrawable(emoji) }

  if (drawable != null) {
    Image(
      painter = rememberDrawablePainter(drawable),
      contentDescription = emoji,
      modifier = modifier.size(26.dp)
    )
  } else {
    Text(
      text = emoji,
      fontSize = if (emoji.isAsciiEmoticon()) 13.sp else 22.sp,
      maxLines = 1,
      softWrap = false,
      modifier = modifier
    )
  }
}

private fun String.isAsciiEmoticon(): Boolean = all { it.code < 128 }

private fun EmojiKeyboardCategory.icon(): ImageVector {
  return when (this) {
    EmojiKeyboardCategory.RECENTS -> Icons.Outlined.Schedule
    EmojiKeyboardCategory.PEOPLE -> Icons.Outlined.EmojiEmotions
    EmojiKeyboardCategory.NATURE -> Icons.Outlined.EmojiNature
    EmojiKeyboardCategory.FOODS -> Icons.Outlined.EmojiFoodBeverage
    EmojiKeyboardCategory.ACTIVITY -> Icons.Outlined.EmojiEvents
    EmojiKeyboardCategory.PLACES -> Icons.Outlined.EmojiTransportation
    EmojiKeyboardCategory.OBJECTS -> Icons.Outlined.EmojiObjects
    EmojiKeyboardCategory.SYMBOLS -> Icons.Outlined.EmojiSymbols
    EmojiKeyboardCategory.FLAGS -> Icons.Outlined.EmojiFlags
    EmojiKeyboardCategory.EMOTICONS -> Icons.Outlined.SentimentSatisfied
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EmojiPageScreenPreview() {
  EmojiPageScreen(
    state = EmojiPageState(
      pages = listOf(
        EmojiCategoryPage(
          category = EmojiKeyboardCategory.PEOPLE,
          emoji = listOf("😀", "😂", "🥰", "😎", "🤔", "😢", "😡", "🥳").map { KeyboardEmoji(it) } +
            KeyboardEmoji("👍", listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))
        ),
        EmojiCategoryPage(
          category = EmojiKeyboardCategory.NATURE,
          emoji = listOf("🐶", "🐱", "🦊", "🌸", "🌈").map { KeyboardEmoji(it) }
        )
      ),
      selectedCategory = EmojiKeyboardCategory.PEOPLE
    ),
    onEvent = {}
  )
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.screens.GRID_CONTENT_PADDING
import org.signal.mediakeyboard.screens.MediaKeyboardSearchField
import org.signal.mediakeyboard.screens.PinnedRailLayout
import org.signal.mediakeyboard.screens.SearchFieldReveal

/**
 * @param onSearchFieldRevealedChange Reports whether the grid is scrolled far enough up to show the
 *   search field, so the top bar can drop its now-redundant search icon.
 */
@Composable
fun StickerPageScreen(
  state: StickerPageState,
  onEvent: (StickerPageScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  onSearchFieldRevealedChange: (Boolean) -> Unit = {}
) {
  PinnedRailLayout(
    rail = { StickerPackRail(state = state, onEvent = onEvent) },
    modifier = modifier
  ) {
    StickerGrid(
      state = state,
      onEvent = onEvent,
      onSearchFieldRevealedChange = onSearchFieldRevealedChange
    )
  }
}

@Composable
private fun StickerPackRail(
  state: StickerPageState,
  onEvent: (StickerPageScreenEvents) -> Unit
) {
  Surface(color = SignalTheme.colors.colorSurface5) {
    LazyRow(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
      items(state.packs, key = { it.id }) { pack ->
        PackButton(
          pack = pack,
          selected = pack.id == state.selectedPackId,
          onClick = { onEvent(StickerPageScreenEvents.PackSelected(pack.id)) }
        )
      }
    }
  }
}

@Composable
private fun PackButton(
  pack: KeyboardStickerPack,
  selected: Boolean,
  onClick: () -> Unit
) {
  IconButton(onClick = onClick) {
    val backgroundModifier = if (selected) {
      Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
    } else {
      Modifier
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = backgroundModifier.size(36.dp)
    ) {
      if (pack.id == StickerKeyboardRepository.RECENT_PACK_ID) {
        Icon(
          imageVector = Icons.Outlined.Schedule,
          contentDescription = stringResource(R.string.MediaKeyboard__recently_used),
          tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        GlideImage(
          model = pack.cover,
          modifier = Modifier.size(28.dp)
        )
      }
    }
  }
}

@Composable
private fun StickerGrid(
  state: StickerPageState,
  onEvent: (StickerPageScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  onSearchFieldRevealedChange: (Boolean) -> Unit = {}
) {
  val gridState = rememberLazyGridState()

  // The search field is the first item, so everything else starts one along from it.
  val headerIndices = remember(state.packs) {
    var index = 1
    buildMap {
      state.packs.forEach { pack ->
        put(pack.id, index)
        index += 1 + pack.stickers.size
      }
    }
  }

  SearchFieldReveal(
    hasContent = state.packs.isNotEmpty(),
    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
    scrollPastField = gridState::scrollToItem,
    onRevealedChange = onSearchFieldRevealedChange
  )

  LaunchedEffect(state.scrollTargetPackId) {
    val target = state.scrollTargetPackId ?: return@LaunchedEffect
    headerIndices[target]?.let { gridState.scrollToItem(it) }
    onEvent(StickerPageScreenEvents.ScrollTargetConsumed)
  }

  val visiblePackId by remember(state.packs, headerIndices) {
    derivedStateOf {
      val firstVisible = gridState.firstVisibleItemIndex
      state.packs
        .lastOrNull { pack -> (headerIndices[pack.id] ?: Int.MAX_VALUE) <= firstVisible }
        ?.id
    }
  }

  LaunchedEffect(visiblePackId) {
    visiblePackId?.let { onEvent(StickerPageScreenEvents.VisiblePackChanged(it)) }
  }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 72.dp),
    state = gridState,
    contentPadding = GRID_CONTENT_PADDING,
    modifier = modifier.fillMaxWidth()
  ) {
    item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
      MediaKeyboardSearchField(
        hint = stringResource(R.string.MediaKeyboard__search_stickers),
        onClick = { onEvent(StickerPageScreenEvents.SearchClicked) }
      )
    }

    state.packs.forEach { pack ->
      item(key = "header:${pack.id}", span = { GridItemSpan(maxLineSpan) }) {
        StickerPackHeader(pack = pack, onEvent = onEvent)
      }

      pack.stickers.forEachIndexed { index, sticker ->
        item(key = "${pack.id}:${sticker.stickerId}:$index") {
          StickerCell(
            sticker = sticker,
            allowAnimation = state.allowAnimation,
            onEvent = onEvent
          )
        }
      }
    }
  }
}

@Composable
private fun StickerPackHeader(
  pack: KeyboardStickerPack,
  onEvent: (StickerPageScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val isRecents = pack.id == StickerKeyboardRepository.RECENT_PACK_ID
  val menuController = remember { DropdownMenus.MenuController() }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
  ) {
    Text(
      text = if (isRecents) stringResource(R.string.MediaKeyboard__recently_used) else pack.title.orEmpty(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    Box {
      IconButton(
        onClick = { menuController.show() },
        modifier = Modifier.size(32.dp)
      ) {
        Icon(
          imageVector = SignalIcons.MoreVertical.imageVector,
          contentDescription = stringResource(R.string.MediaKeyboard__more_options),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      StickerPackHeaderMenu(
        pack = pack,
        isRecents = isRecents,
        menuController = menuController,
        onEvent = onEvent
      )
    }
  }
}

@Composable
private fun StickerPackHeaderMenu(
  pack: KeyboardStickerPack,
  isRecents: Boolean,
  menuController: DropdownMenus.MenuController,
  onEvent: (StickerPageScreenEvents) -> Unit
) {
  DropdownMenus.Menu(
    controller = menuController,
    offsetX = 0.dp
  ) {
    if (isRecents) {
      DropdownMenus.ItemWithIcon(
        menuController = menuController,
        imageVector = SignalIcons.Trash.imageVector,
        stringResId = R.string.MediaKeyboard__clear_recents,
        onClick = {
          onEvent(StickerPageScreenEvents.ClearRecentStickersClicked)
        }
      )

      return@Menu
    }

    // Everything below acts on the pack itself, which needs the key the recents pack does not have.
    val packKey = pack.packKey ?: return@Menu

    DropdownMenus.ItemWithIcon(
      menuController = menuController,
      imageVector = SignalIcons.Send.imageVector,
      stringResId = R.string.MediaKeyboard__send,
      onClick = {
        onEvent(StickerPageScreenEvents.SendStickerPackClicked(pack.id, packKey))
      }
    )

    DropdownMenus.ItemWithIcon(
      menuController = menuController,
      imageVector = SignalIcons.StickerPack.imageVector,
      stringResId = R.string.MediaKeyboard__view_pack,
      onClick = {
        onEvent(StickerPageScreenEvents.ViewStickerPackClicked(pack.id, packKey))
      }
    )

    DropdownMenus.ItemWithIcon(
      menuController = menuController,
      imageVector = SignalIcons.MinusCircle.imageVector,
      stringResId = R.string.MediaKeyboard__remove_pack,
      onClick = {
        onEvent(StickerPageScreenEvents.RemoveStickerPackClicked(pack.id, packKey))
      }
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun StickerPackHeaderPreview() {
  Column {
    StickerPackHeader(
      pack = KeyboardStickerPack(
        id = StickerKeyboardRepository.RECENT_PACK_ID,
        packKey = null,
        title = null,
        cover = null,
        stickers = emptyList()
      ),
      onEvent = {}
    )

    StickerPackHeader(
      pack = KeyboardStickerPack(
        id = "pack-1",
        packKey = "pack-1-key",
        title = "Bandit the Cat",
        cover = null,
        stickers = emptyList()
      ),
      onEvent = {}
    )
  }
}

@Composable
private fun StickerCell(
  sticker: KeyboardSticker,
  allowAnimation: Boolean,
  onEvent: (StickerPageScreenEvents) -> Unit
) {
  val controller = remember { DropdownMenus.MenuController() }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .aspectRatio(1f)
      .clip(MaterialTheme.shapes.medium)
      .combinedClickable(
        onClick = {
          onEvent(StickerPageScreenEvents.StickerClicked(sticker))
        },
        onLongClick = {
          controller.show()
        }
      )
      .padding(8.dp)
  ) {
    GlideImage(
      model = sticker.image,
      enableApngAnimation = allowAnimation && sticker.isAnimated,
      skipMemoryCache = true,
      modifier = Modifier.fillMaxSize()
    )

    DropdownMenus.Menu(
      controller = controller
    ) {
      DropdownMenus.ItemWithIcon(
        menuController = controller,
        imageVector = SignalIcons.Send.imageVector,
        stringResId = R.string.MediaKeyboard__send,
        onClick = {
          onEvent(StickerPageScreenEvents.StickerClicked(sticker))
        }
      )

      DropdownMenus.ItemWithIcon(
        menuController = controller,
        imageVector = SignalIcons.StickerPack.imageVector,
        stringResId = R.string.MediaKeyboard__view_pack,
        onClick = {
          onEvent(StickerPageScreenEvents.ViewStickerPackClicked(sticker.packId, sticker.packKey))
        }
      )
    }
  }
}

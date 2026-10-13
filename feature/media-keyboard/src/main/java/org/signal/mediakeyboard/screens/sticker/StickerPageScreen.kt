/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.keyboard.LocalKeyboardSheetController
import org.signal.glide.compose.GlideImage
import org.signal.mediakeyboard.R
import org.signal.mediakeyboard.data.KeyboardSticker
import org.signal.mediakeyboard.data.KeyboardStickerPack
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.screens.CollapsingHeaderLayout
import org.signal.mediakeyboard.screens.GRID_CONTENT_PADDING
import org.signal.mediakeyboard.screens.MediaKeyboardSearchField
import org.signal.mediakeyboard.screens.PinnedRailLayout
import org.signal.mediakeyboard.screens.SEARCH_FIELD_SPACING
import org.signal.core.ui.R as CoreUiR

private const val STICKER_COLUMN_COUNT = 5
private val STICKER_CELL_SPACING = 12.dp

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

  if (state.confirmRemovePack != null) {
    ConfirmRemovePackDialog(onEvent = onEvent)
  }

  if (state.confirmRemoveFavorite != null) {
    ConfirmRemoveFavoriteDialog(onEvent = onEvent)
  }
}

@Composable
private fun ConfirmRemoveFavoriteDialog(onEvent: (StickerPageScreenEvents) -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(CoreUiR.string.StickerFavorites__remove_from_favorites_question),
    body = stringResource(CoreUiR.string.StickerFavorites__you_cant_send_it_or_add_it_to_favorites_again),
    confirm = stringResource(CoreUiR.string.StickerFavorites__remove),
    dismiss = stringResource(android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = { onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesConfirmed) },
    onDeny = { onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesCanceled) },
    onDismissRequest = { onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesCanceled) }
  )
}

@Composable
private fun ConfirmRemovePackDialog(onEvent: (StickerPageScreenEvents) -> Unit) {
  val host = LocalKeyboardSheetController.current

  // A window of our own in front of the sheet, so the sheet stays put rather than giving way to it.
  DisposableEffect(Unit) {
    host.onHostWindowShown()
    onDispose { host.onHostWindowHidden() }
  }

  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.MediaKeyboard__remove_sticker_pack_question),
    body = stringResource(R.string.MediaKeyboard__this_will_remove_the_sticker_pack),
    confirm = stringResource(R.string.MediaKeyboard__remove_pack),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(StickerPageScreenEvents.RemoveStickerPackConfirmed) },
    onDeny = { onEvent(StickerPageScreenEvents.RemoveStickerPackCanceled) },
    onDismissRequest = { onEvent(StickerPageScreenEvents.RemoveStickerPackCanceled) }
  )
}

@Composable
private fun StickerPackRail(
  state: StickerPageState,
  onEvent: (StickerPageScreenEvents) -> Unit
) {
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
      when (pack.id) {
        StickerKeyboardRepository.FAVORITES_PACK_ID -> {
          Icon(
            imageVector = SignalIcons.Favorite.imageVector,
            contentDescription = stringResource(R.string.MediaKeyboard__favorites),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        StickerKeyboardRepository.RECENT_PACK_ID -> {
          Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = stringResource(R.string.MediaKeyboard__recently_used),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        else -> {
          GlideImage(
            model = pack.cover,
            imageSize = DpSize(28.dp, 28.dp),
            modifier = Modifier.size(28.dp)
          )
        }
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

  val gridPacks = remember(state.packs) { state.packs.filterNot { it.isEmptyFavorites } }

  val headerIndices = remember(gridPacks) {
    var index = 0
    buildMap {
      gridPacks.forEach { pack ->
        put(pack.id, index)
        index += 1 + pack.stickers.size
      }
    }
  }

  LaunchedEffect(state.scrollTargetPackId) {
    val target = state.scrollTargetPackId ?: return@LaunchedEffect
    headerIndices[target]?.let { gridState.scrollToItem(it) }
    onEvent(StickerPageScreenEvents.ScrollTargetConsumed)
  }

  val visiblePackId by remember(gridPacks, headerIndices) {
    derivedStateOf {
      val firstVisible = gridState.firstVisibleItemIndex
      gridPacks
        .lastOrNull { pack -> (headerIndices[pack.id] ?: Int.MAX_VALUE) <= firstVisible }
        ?.id
    }
  }

  LaunchedEffect(visiblePackId) {
    visiblePackId?.let { onEvent(StickerPageScreenEvents.VisiblePackChanged(it)) }
  }

  CollapsingHeaderLayout(
    header = {
      MediaKeyboardSearchField(
        hint = stringResource(R.string.MediaKeyboard__search_stickers),
        onClick = { onEvent(StickerPageScreenEvents.SearchClicked) },
        modifier = Modifier.padding(bottom = SEARCH_FIELD_SPACING)
      )
    },
    onRevealedChange = onSearchFieldRevealedChange,
    modifier = modifier
  ) {
    // Each cell decodes its sticker at the cell's size, which the grid does not report, so work it out the way the
    // grid will and hand it down.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val layoutDirection = LocalLayoutDirection.current
      val rowWidth = maxWidth -
        GRID_CONTENT_PADDING.calculateStartPadding(layoutDirection) -
        GRID_CONTENT_PADDING.calculateEndPadding(layoutDirection)
      val cellSize = (rowWidth - STICKER_CELL_SPACING * (STICKER_COLUMN_COUNT - 1)) / STICKER_COLUMN_COUNT

      LazyVerticalGrid(
        columns = GridCells.Fixed(STICKER_COLUMN_COUNT),
        state = gridState,
        contentPadding = GRID_CONTENT_PADDING,
        horizontalArrangement = Arrangement.spacedBy(STICKER_CELL_SPACING),
        verticalArrangement = Arrangement.spacedBy(STICKER_CELL_SPACING),
        modifier = Modifier.fillMaxWidth()
      ) {
        gridPacks.forEach { pack ->
          item(key = "header:${pack.id}", span = { GridItemSpan(maxLineSpan) }) {
            StickerPackHeader(pack = pack, onEvent = onEvent)
          }

          pack.stickers.forEachIndexed { index, sticker ->
            item(key = "${pack.id}:${sticker.stickerId}:$index") {
              StickerCell(
                sticker = sticker,
                isInFavorites = pack.id == StickerKeyboardRepository.FAVORITES_PACK_ID,
                favoritesEnabled = state.favoritesEnabled,
                allowAnimation = state.allowAnimation,
                cellSize = cellSize,
                onEvent = onEvent
              )
            }
          }
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
  val isFavorites = pack.id == StickerKeyboardRepository.FAVORITES_PACK_ID
  val menuController = remember { DropdownMenus.MenuController() }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
  ) {
    Text(
      text = when {
        isFavorites -> stringResource(R.string.MediaKeyboard__favorites)
        isRecents -> stringResource(R.string.MediaKeyboard__recently_used)
        else -> pack.title.orEmpty()
      },
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    if (!isFavorites) {
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
        id = StickerKeyboardRepository.FAVORITES_PACK_ID,
        packKey = null,
        title = null,
        cover = null,
        stickers = emptyList()
      ),
      onEvent = {}
    )

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
  isInFavorites: Boolean,
  favoritesEnabled: Boolean,
  allowAnimation: Boolean,
  cellSize: Dp,
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
        onDoubleClick = {
          onEvent(StickerPageScreenEvents.StickerSendClicked(sticker))
        },
        onLongClick = {
          controller.show()
        }
      )
  ) {
    GlideImage(
      model = sticker.image,
      enableApngAnimation = allowAnimation,
      skipMemoryCache = true,
      imageSize = DpSize(cellSize, cellSize),
      contentScale = ContentScale.Fit,
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
          onEvent(StickerPageScreenEvents.StickerSendClicked(sticker))
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

      if (isInFavorites) {
        DropdownMenus.ItemWithIcon(
          menuController = controller,
          imageVector = SignalIcons.Transfer.imageVector,
          stringResId = R.string.MediaKeyboard__move_to_top,
          onClick = {
            onEvent(StickerPageScreenEvents.MoveFavoriteToTopClicked(sticker))
          }
        )
      }

      if (favoritesEnabled && sticker.isFavorite) {
        DropdownMenus.ItemWithIcon(
          menuController = controller,
          imageVector = SignalIcons.FavoriteOff.imageVector,
          stringResId = CoreUiR.string.StickerFavorites__remove_from_favorites,
          onClick = {
            onEvent(StickerPageScreenEvents.RemoveStickerFromFavoritesClicked(sticker))
          }
        )
      } else if (favoritesEnabled) {
        DropdownMenus.ItemWithIcon(
          menuController = controller,
          imageVector = SignalIcons.Favorite.imageVector,
          stringResId = CoreUiR.string.StickerFavorites__add_to_favorites,
          onClick = {
            onEvent(StickerPageScreenEvents.AddStickerToFavoritesClicked(sticker))
          }
        )
      }
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.select

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.window.core.layout.WindowSizeClass
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.ensureWidthIsAtLeastHeight
import org.signal.glide.compose.GlideImage
import org.signal.mediasend.MediaSendMetrics
import org.signal.mediasend.MediaSendNavKey
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.edit.rememberPreviewMedia
import org.signal.mediasend.goToEdit
import org.signal.mediasend.pop

/**
 * Allows user to select one or more pieces of content to add to the
 * current media selection.
 */
@Composable
internal fun MediaSelectScreen(
  state: MediaSendState,
  backStack: NavBackStack<NavKey>,
  callback: MediaSelectScreenCallback
) {
  val gridConfiguration = rememberGridConfiguration(state.selectedMediaFolder == null)

  Scaffolds.Settings(
    title = state.selectedMediaFolder?.title ?: "Gallery",
    navigationIcon = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_arrow_start_24),
    onNavigationClick = {
      if (state.selectedMediaFolder != null) {
        callback.onFolderClick(null)
      } else {
        backStack.pop()
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier.padding(paddingValues)
    ) {
      LazyVerticalGrid(
        columns = gridConfiguration.gridCells,
        horizontalArrangement = spacedBy(gridConfiguration.horizontalSpacing),
        verticalArrangement = spacedBy(gridConfiguration.verticalSpacing),
        modifier = Modifier
          .padding(horizontal = gridConfiguration.horizontalMargin)
          .weight(1f)
      ) {
        if (state.selectedMediaFolder == null) {
          items(state.mediaFolders, key = { it.bucketId }) {
            MediaFolderTile(it, callback)
          }
        } else {
          items(state.selectedMediaFolderItems, key = { it.uri }) { media ->
            MediaTile(media = media, state.selectedMedia.indexOfFirst { it.uri == media.uri }, callback = callback)
          }
        }
      }

      AnimatedVisibility(
        visible = state.selectedMedia.isNotEmpty(),
        enter = expandVertically(
          expandFrom = Alignment.Top,
          animationSpec = spring()
        ) + fadeIn(animationSpec = spring()),
        exit = shrinkVertically(
          shrinkTowards = Alignment.Top,
          animationSpec = spring()
        ) + fadeOut(animationSpec = spring()),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(vertical = gridConfiguration.bottomBarVerticalPadding, horizontal = gridConfiguration.bottomBarHorizontalPadding)
        ) {
          LazyRow(
            modifier = Modifier
              .weight(1f)
              .padding(end = 16.dp),
            horizontalArrangement = spacedBy(space = 12.dp, alignment = gridConfiguration.bottomBarAlignment)
          ) {
            items(state.selectedMedia, key = { it.uri }) { media ->
              MediaThumbnail(media, modifier = Modifier.animateItem()) {
                callback.setFocusedMedia(media)
                backStack.goToEdit()
              }
            }
          }

          NextButton(state.selectedMedia.size) {
            backStack.goToEdit()
          }
        }
      }
    }
  }
}

@Composable
private fun rememberGridConfiguration(isRootGrid: Boolean): GridConfiguration {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass, isRootGrid) {
    GridConfiguration(
      gridCells = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = GridCells.Fixed(6),
          medium = GridCells.Fixed(4),
          compact = GridCells.Fixed(2)
        )
      } else {
        windowSizeClass.forWidthBreakpoint(
          expanded = GridCells.Fixed(8),
          medium = GridCells.Fixed(6),
          compact = GridCells.Fixed(4)
        )
      },
      horizontalMargin = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 38.dp,
          medium = 35.dp,
          compact = 24.dp
        )
      } else {
        0.dp
      },
      horizontalSpacing = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 32.dp,
          medium = 28.dp,
          compact = 16.dp
        )
      } else {
        4.dp
      },
      verticalSpacing = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 32.dp,
          medium = 32.dp,
          compact = 24.dp
        )
      } else {
        4.dp
      },
      bottomBarVerticalPadding = windowSizeClass.forWidthBreakpoint(
        expanded = 16.dp,
        medium = 16.dp,
        compact = 8.dp
      ),
      bottomBarHorizontalPadding = windowSizeClass.forWidthBreakpoint(
        expanded = 24.dp,
        medium = 24.dp,
        compact = 16.dp
      ),
      bottomBarAlignment = windowSizeClass.forWidthBreakpoint(
        expanded = Alignment.End,
        medium = Alignment.End,
        compact = Alignment.Start
      )
    )
  }
}

private fun <T> WindowSizeClass.forWidthBreakpoint(
  expanded: T,
  medium: T,
  compact: T
): T {
  return when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> expanded
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> medium
    else -> compact
  }
}

@Composable
private fun MediaFolderTile(
  mediaFolder: MediaFolder,
  callback: MediaSelectScreenCallback
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        onClick = { callback.onFolderClick(mediaFolder) },
        onClickLabel = mediaFolder.title,
        role = Role.Button
      ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (LocalInspectionMode.current) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .background(color = Previews.rememberRandomColor(), shape = RoundedCornerShape(26.dp))
      )
    } else {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(26.dp))
      ) {
        val width = maxWidth
        val height = maxHeight

        GlideImage(
          model = mediaFolder.thumbnailUri,
          imageSize = DpSize(width, height),
          modifier = Modifier
            .aspectRatio(1f)
        )
      }
    }

    Text(text = mediaFolder.title, modifier = Modifier.padding(top = 12.dp))
  }
}

@Composable
private fun MediaTile(
  media: Media,
  selectionIndex: Int,
  callback: MediaSelectScreenCallback
) {
  val scale by animateFloatAsState(
    targetValue = if (selectionIndex >= 0) {
      0.8f
    } else {
      1f
    }
  )

  val cornerClip by animateDpAsState(
    if (selectionIndex >= 0) 12.dp else 0.dp
  )

  BoxWithConstraints(
    modifier = Modifier
      .background(color = MaterialTheme.colorScheme.surfaceVariant)
      .clickable(
        onClick = { callback.onMediaClick(media) },
        onClickLabel = media.fileName,
        role = Role.Button
      )
  ) {
    if (LocalInspectionMode.current) {
      Box(
        modifier = Modifier
          .scale(scale)
          .background(color = Previews.rememberRandomColor(), shape = RoundedCornerShape(cornerClip)).fillMaxWidth()
          .aspectRatio(1f)
      )
    } else {
      val width = maxWidth
      val height = maxHeight

      GlideImage(
        model = media.uri,
        imageSize = DpSize(width, height),
        modifier = Modifier
          .aspectRatio(1f)
          .scale(scale)
          .clip(RoundedCornerShape(cornerClip))
      )
    }
  }

  if (selectionIndex >= 0) {
    Box(
      modifier = Modifier.padding(3.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .border(width = 3.dp, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
          .padding(1.dp)
          .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(percent = 50))
          .ensureWidthIsAtLeastHeight()
          .padding(horizontal = 5.5.dp, vertical = 2.dp)
      ) {
        Text(text = "${selectionIndex + 1}", color = MaterialTheme.colorScheme.onPrimary)
      }
    }
  }
}

@Composable
private fun NextButton(mediaSelectionCount: Int, onClick: () -> Unit) {
  Buttons.MediumTonal(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Box(
      modifier = Modifier
        .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(percent = 50))
        .ensureWidthIsAtLeastHeight()
    ) {
      Text(
        text = "$mediaSelectionCount",
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
      )
    }

    Icon(
      imageVector = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_chevron_right_24),
      contentDescription = "Next"
    )
  }
}

@Composable
private fun MediaThumbnail(
  media: Media,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  if (LocalInspectionMode.current) {
    Box(
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .background(color = Previews.rememberRandomColor(), shape = RoundedCornerShape(8.dp))
    )
  } else {
    GlideImage(
      model = media.uri,
      imageSize = MediaSendMetrics.SelectedMediaPreviewSize,
      modifier = modifier
        .size(MediaSendMetrics.SelectedMediaPreviewSize)
        .clip(RoundedCornerShape(8.dp))
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenFolderPreview() {
  Previews.Preview {
    MediaSelectScreen(
      state = MediaSendState(
        mediaFolders = rememberPreviewMediaFolders(20)
      ),
      backStack = rememberNavBackStack(MediaSendNavKey.Edit),
      callback = MediaSelectScreenCallback.Empty
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenMediaPreview() {
  val folders = rememberPreviewMediaFolders(20)
  val media = rememberPreviewMedia(100)
  val selectedMedia: MutableList<Media> = remember { mutableStateListOf() }
  val callback = remember {
    object : MediaSelectScreenCallback by MediaSelectScreenCallback.Empty {
      override fun onMediaClick(media: Media) {
        if (media in selectedMedia) {
          selectedMedia.remove(media)
        } else {
          selectedMedia.add(media)
        }
      }
    }
  }

  Previews.Preview {
    MediaSelectScreen(
      state = MediaSendState(
        mediaFolders = folders,
        selectedMediaFolder = folders.first(),
        selectedMediaFolderItems = media,
        selectedMedia = selectedMedia
      ),
      backStack = rememberNavBackStack(MediaSendNavKey.Edit),
      callback = callback
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaFolderTilePreview() {
  Previews.Preview {
    Box(modifier = Modifier.width(174.dp)) {
      MediaFolderTile(
        mediaFolder = rememberPreviewMediaFolders(1).first(),
        callback = MediaSelectScreenCallback.Empty
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaTilePreview() {
  Previews.Preview {
    MediaTile(
      media = rememberPreviewMedia(1).first(),
      selectionIndex = -1,
      callback = MediaSelectScreenCallback.Empty
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaTileSelectedPreview() {
  var isSelected by remember { mutableStateOf(true) }

  Previews.Preview {
    MediaTile(
      media = rememberPreviewMedia(1).first(),
      selectionIndex = if (isSelected) 0 else -1,
      callback = object : MediaSelectScreenCallback by MediaSelectScreenCallback.Empty {
        override fun onMediaClick(media: Media) {
          isSelected = !isSelected
        }
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun NextButtonPreview() {
  Previews.Preview {
    NextButton(
      mediaSelectionCount = 3,
      onClick = {}
    )
  }
}

@Composable
private fun rememberPreviewMediaFolders(count: Int): List<MediaFolder> {
  return remember(count) {
    (0 until count).map { index ->
      MediaFolder(
        thumbnailUri = "https://example.com/folder$index.jpg".toUri(),
        title = "Folder $index",
        itemCount = (index + 1) * 10,
        bucketId = "bucket_$index",
        folderType = if (index == 0) MediaFolder.FolderType.CAMERA else MediaFolder.FolderType.NORMAL
      )
    }
  }
}

private data class GridConfiguration(
  val gridCells: GridCells,
  val horizontalMargin: Dp,
  val horizontalSpacing: Dp,
  val verticalSpacing: Dp,
  val bottomBarVerticalPadding: Dp,
  val bottomBarHorizontalPadding: Dp,
  val bottomBarAlignment: Alignment.Horizontal
)

interface MediaSelectScreenCallback {
  fun onFolderClick(mediaFolder: MediaFolder?)
  fun onMediaClick(media: Media)
  fun setFocusedMedia(media: Media)

  object Empty : MediaSelectScreenCallback {
    override fun onFolderClick(mediaFolder: MediaFolder?) {}
    override fun onMediaClick(media: Media) {}
    override fun setFocusedMedia(media: Media) {}
  }
}

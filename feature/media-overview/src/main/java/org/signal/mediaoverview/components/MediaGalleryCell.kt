/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ByteSize
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.glide.load.SignalDownsampleStrategy
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewSharedElements
import org.signal.mediaoverview.MediaOverviewTestTags
import org.signal.mediaoverview.R

private const val SELECTED_SCALE = 0.83f
private const val SELECTION_ANIMATION_MILLIS = 150

/**
 * A square thumbnail in the media grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaGalleryCell(
  item: MediaOverviewItem,
  isSelected: Boolean,
  showFileSize: Boolean,
  isContextMenuOpen: Boolean,
  sharedElements: MediaOverviewSharedElements,
  onEvent: (MediaOverviewEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val scale by animateFloatAsState(
    targetValue = if (isSelected) SELECTED_SCALE else 1f,
    animationSpec = tween(SELECTION_ANIMATION_MILLIS),
    label = "selection-scale"
  )
  val selectedAlpha by animateFloatAsState(
    targetValue = if (isSelected) 1f else 0f,
    animationSpec = tween(SELECTION_ANIMATION_MILLIS),
    label = "selection-alpha"
  )
  val contentDescription = stringResource(R.string.MediaOverviewScreen__media_preview)

  Box(
    modifier = modifier
      .aspectRatio(1f)
      .combinedClickable(
        onClick = { onEvent(MediaOverviewEvent.ItemClicked(item)) },
        onLongClick = { onEvent(MediaOverviewEvent.ItemLongClicked(item)) }
      )
      .semantics { this.contentDescription = contentDescription }
      .testTag(MediaOverviewTestTags.item(item.key))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
    ) {
      GridThumbnail(
        item = item,
        sharedElements = sharedElements,
        modifier = Modifier.fillMaxSize()
      )

      MediaThumbnailOverlays(
        item = item,
        playOverlaySize = 48.dp,
        modifier = Modifier.fillMaxSize()
      )
    }

    if (showFileSize) {
      Text(
        text = ByteSize(item.size).toUnitString(maxPlaces = 2),
        color = SignalTheme.colors.colorOnCustom,
        fontSize = 12.sp,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(4.dp)
          .background(SignalTheme.colors.colorTransparentInverse4, RoundedCornerShape(10.dp))
          .padding(horizontal = 6.dp)
      )
    }

    SelectedCheck(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .alpha(selectedAlpha)
    )

    if (isContextMenuOpen) {
      MediaContextMenu(item = item, onEvent = onEvent)
    }
  }
}

@Composable
private fun GridThumbnail(
  item: MediaOverviewItem,
  sharedElements: MediaOverviewSharedElements,
  modifier: Modifier = Modifier
) {
  if (LocalInspectionMode.current) {
    Box(modifier = modifier.background(SignalTheme.colors.colorSurface3))
    return
  }

  var imageView: ImageView? by remember { mutableStateOf(null) }

  Box(modifier = modifier.background(if (item.displayUri == null) SignalTheme.colors.colorSurface3 else Color.Transparent)) {
    if (item.blurHash != null) {
      GlideImage(
        model = item.blurHash,
        scaleType = GlideImageScaleType.CENTER_CROP,
        diskCacheStrategy = DiskCacheStrategy.NONE,
        modifier = Modifier.fillMaxSize()
      )
    }

    AndroidView(
      factory = { context ->
        ImageView(context).apply {
          scaleType = ImageView.ScaleType.CENTER_CROP
        }.also { imageView = it }
      },
      update = { view ->
        val displayUri = item.displayUri
        if (view.tag != displayUri) {
          view.tag = displayUri
          if (displayUri == null) {
            Glide.with(view).clear(view)
            view.setImageDrawable(null)
          } else {
            Glide.with(view)
              .load(DecryptableUri(displayUri, 0, if (item.uri != null) item.contentType else null))
              .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
              .downsample(SignalDownsampleStrategy.CENTER_OUTSIDE_NO_UPSCALE)
              .dontAnimate()
              .transition(DrawableTransitionOptions.withCrossFade())
              .centerCrop()
              .into(view)
          }
        }
      },
      onReset = { view ->
        Glide.with(view).clear(view)
        view.tag = null
      },
      onRelease = { view -> Glide.with(view).clear(view) },
      modifier = Modifier.fillMaxSize()
    )
  }

  DisposableEffect(item.key, imageView) {
    val view = imageView
    if (view != null) {
      sharedElements.register(item.key, view)
    }

    onDispose {
      if (view != null) {
        sharedElements.unregister(item.key, view)
      }
    }
  }
}

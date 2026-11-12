/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ByteSize
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.glide.load.SignalDownsampleStrategy
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewSharedElements
import org.signal.mediaoverview.MediaOverviewTestTags
import org.signal.mediaoverview.R

private const val SELECTED_SCALE = 0.83f
private const val SELECTION_ANIMATION_MILLIS = 150
private const val FADE_DURATION_MS = 250

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
  var imageState by remember(item.displayUri) { mutableStateOf(ImageState.LOADING) }
  val blurHashAlpha by animateFloatAsState(
    targetValue = if (imageState == ImageState.LOADING) 1f else 0f,
    animationSpec = if (imageState == ImageState.FADING_IN) tween(FADE_DURATION_MS) else snap(),
    label = "blur-hash-alpha"
  )

  Box(modifier = modifier.background(if (item.displayUri == null) SignalTheme.colors.colorSurface3 else SignalTheme.colors.colorSurface2)) {
    val blurHash = item.blurHash
    if (blurHash != null && blurHashAlpha > 0f) {
      BlurHashImage(
        blurHash = blurHash,
        modifier = Modifier
          .fillMaxSize()
          .alpha(blurHashAlpha)
      )
    }

    AndroidView(
      factory = { context -> ThumbnailContainer(context) },
      update = { container ->
        val view = container.imageView
        imageView = view
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
              .transition(DrawableTransitionOptions.withCrossFade(FADE_DURATION_MS))
              .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false

                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                  imageState = if (dataSource == DataSource.MEMORY_CACHE) ImageState.SHOWN else ImageState.FADING_IN
                  return false
                }
              })
              .centerCrop()
              .into(view)
          }
        }
      },
      onReset = { container ->
        val view = container.imageView
        Glide.with(view).clear(view)
        view.tag = null
        view.transitionName = null
      },
      onRelease = { container -> Glide.with(container.imageView).clear(container.imageView) },
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

private enum class ImageState {
  LOADING,
  FADING_IN,
  SHOWN
}

/**
 * Activity transitions hide the shared element by changing its visibility and then invalidating its parent. Compose
 * ignores invalidations of an AndroidView's root, so the image needs a parent that Compose does observe.
 */
private class ThumbnailContainer(context: Context) : FrameLayout(context) {
  val imageView = ImageView(context).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
  }

  init {
    addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
  }
}

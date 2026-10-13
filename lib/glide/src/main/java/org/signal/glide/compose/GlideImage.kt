/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.glide.compose

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.bumptech.glide.Glide
import com.bumptech.glide.TransitionOptions
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.signal.glide.apng.ApngOptions

private const val FADE_IN_DURATION_MILLIS = 200

/**
 * Our very own GlideImage. The GlideImage composable provided by the bumptech library is not suitable because it was is using our encrypted cache decoder/encoder.
 *
 * @param contentScale How the loaded drawable is scaled into the available space. This is applied when drawing, so
 *   unlike [scaleType], which becomes a Glide request transform, it also reaches an animated APNG.
 * @param enableApngAnimation Plays the model as an animated APNG when it is one.
 * @param skipMemoryCache Set this when the same model is loaded at several sizes, so that a stateful resource such as
 *   an APNG frame decoder is not shared across differently-sized targets.
 * @param fadeIn Fades the image in when it was not already in the memory cache.
 */
@Composable
fun <T> GlideImage(
  modifier: Modifier = Modifier,
  model: T?,
  imageSize: DpSize? = null,
  scaleType: GlideImageScaleType = GlideImageScaleType.FIT_CENTER,
  fallback: Drawable? = null,
  error: Drawable? = fallback,
  transition: TransitionOptions<*, Drawable>? = null,
  diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.ALL,
  contentScale: ContentScale = ContentScale.Crop,
  enableApngAnimation: Boolean = false,
  skipMemoryCache: Boolean = false,
  fadeIn: Boolean = false
) {
  var drawable by remember {
    mutableStateOf<Drawable?>(null)
  }

  var fadeInAlpha by remember {
    mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
  }

  val target = remember {
    object : CustomTarget<Drawable>() {
      override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
        drawable = resource
      }

      override fun onLoadCleared(placeholder: Drawable?) {
        drawable = null
      }
    }
  }

  val density = LocalDensity.current
  val context = LocalContext.current
  DisposableEffect(model, fallback, error, diskCacheStrategy, density, imageSize, enableApngAnimation, skipMemoryCache) {
    val requestManager = Glide.with(context)
    val builder = requestManager
      .load(model)
      .fallback(fallback)
      .error(error)
      .diskCacheStrategy(diskCacheStrategy)
      .set(ApngOptions.ANIMATE, enableApngAnimation)
      .skipMemoryCache(skipMemoryCache)
      .addListener(object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false

        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
          fadeInAlpha = if (fadeIn && dataSource != DataSource.MEMORY_CACHE) Animatable(0f) else null
          return false
        }
      })
      .apply {
        scaleType.applyTo(this)
        transition?.let(this::transition)
      }

    if (imageSize != null) {
      with(density) {
        builder.override(imageSize.width.toPx().toInt(), imageSize.height.toPx().toInt()).into(target)
      }
    } else {
      builder.into(target)
    }

    object : DisposableEffectResult {
      override fun dispose() {
        requestManager.clear(target)
        drawable = null
      }
    }
  }

  LaunchedEffect(fadeInAlpha) {
    fadeInAlpha?.animateTo(1f, tween(FADE_IN_DURATION_MILLIS))
  }

  if (drawable != null) {
    Image(
      painter = rememberDrawablePainter(drawable),
      contentDescription = null,
      contentScale = if (model == null) ContentScale.Inside else contentScale,
      modifier = modifier.graphicsLayer { alpha = fadeInAlpha?.value ?: 1f }
    )
  }
}

enum class GlideImageScaleType {
  /** @see [com.bumptech.glide.request.RequestOptions.fitCenter] */
  FIT_CENTER,

  /** @see [com.bumptech.glide.request.RequestOptions.centerInside] */
  CENTER_INSIDE,

  /** @see [com.bumptech.glide.request.RequestOptions.centerCrop] */
  CENTER_CROP,

  /** @see [com.bumptech.glide.request.RequestOptions.circleCrop] */
  CIRCLE_CROP;

  fun <TranscodeT> applyTo(builder: com.bumptech.glide.RequestBuilder<TranscodeT>): com.bumptech.glide.RequestBuilder<TranscodeT> {
    return when (this) {
      FIT_CENTER -> builder.fitCenter()
      CENTER_INSIDE -> builder.centerInside()
      CENTER_CROP -> builder.centerCrop()
      CIRCLE_CROP -> builder.circleCrop()
    }
  }
}

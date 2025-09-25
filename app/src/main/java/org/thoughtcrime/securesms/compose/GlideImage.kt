/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.TransitionOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.thoughtcrime.securesms.glide.cache.ApngOptions

/**
 * Our very own GlideImage. The GlideImage composable provided by the bumptech library is not suitable because it was is using our encrypted cache decoder/encoder.
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
  enableApngAnimation: Boolean = false
) {
  if (enableApngAnimation) {
    val density = LocalDensity.current

    AndroidView(
      factory = { context -> ImageView(context) },
      update = { imageView ->
        Glide.with(imageView.context)
          .load(model)
          .fallback(fallback)
          .error(error)
          .diskCacheStrategy(diskCacheStrategy)
          .set(ApngOptions.ANIMATE, enableApngAnimation)
          .apply {
            scaleType.applyTo(this)
            transition?.let(this::transition)

            if (imageSize != null) {
              with(density) {
                this@apply.override(imageSize.width.toPx().toInt(), imageSize.height.toPx().toInt())
              }
            }
          }
          .into(imageView)
      },
      modifier = modifier
    )
  } else {
    GlideImage(
      model = model,
      imageSize = imageSize,
      scaleType = scaleType,
      fallback = fallback,
      error = error,
      transition = transition,
      diskCacheStrategy = diskCacheStrategy,
      modifier = modifier
    )
  }
}

@Composable
private fun <T> GlideImage(
  modifier: Modifier = Modifier,
  model: T?,
  imageSize: DpSize? = null,
  scaleType: GlideImageScaleType = GlideImageScaleType.FIT_CENTER,
  fallback: Drawable? = null,
  error: Drawable? = fallback,
  transition: TransitionOptions<*, Drawable>? = null,
  diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.ALL
) {
  var drawable by remember {
    mutableStateOf<Drawable?>(null)
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
  DisposableEffect(model, fallback, error, diskCacheStrategy, density, imageSize) {
    val requestManager = Glide.with(context)
    val builder = requestManager
      .load(model)
      .fallback(fallback)
      .error(error)
      .diskCacheStrategy(diskCacheStrategy)
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

  if (drawable != null) {
    Image(
      painter = rememberDrawablePainter(drawable),
      contentDescription = null,
      contentScale = if (model == null) ContentScale.Inside else ContentScale.Crop,
      modifier = modifier
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

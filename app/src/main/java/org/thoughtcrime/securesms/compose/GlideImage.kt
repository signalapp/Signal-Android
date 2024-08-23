/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

/**
 * Our very own GlideImage.
 */
@Composable
fun <T> GlideImage(
  model: T?,
  modifier: Modifier = Modifier,
  imageSize: DpSize? = null,
  fallback: Drawable? = null,
  error: Drawable? = fallback,
  diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.ALL
) {
  var bitmap by remember {
    mutableStateOf<ImageBitmap?>(null)
  }

  val target = remember {
    object : CustomTarget<Bitmap>() {
      override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
        bitmap = resource.asImageBitmap()
      }

      override fun onLoadCleared(placeholder: Drawable?) {
        bitmap = null
      }
    }
  }

  val density = LocalDensity.current
  val context = LocalContext.current
  DisposableEffect(model, fallback, error, diskCacheStrategy, density, imageSize) {
    val builder = Glide.with(context)
      .asBitmap()
      .load(model)
      .fallback(fallback)
      .error(error)
      .diskCacheStrategy(diskCacheStrategy)
      .fitCenter()

    if (imageSize != null) {
      with(density) {
        builder.override(imageSize.width.toPx().toInt(), imageSize.height.toPx().toInt()).into(target)
      }
    } else {
      builder.into(target)
    }

    object : DisposableEffectResult {
      override fun dispose() {
        Glide.with(context).clear(target)
        bitmap = null
      }
    }
  }

  val bm = bitmap
  if (bm != null) {
    Image(
      bitmap = bm,
      contentDescription = null,
      contentScale = if (model == null) ContentScale.Inside else ContentScale.Crop,
      modifier = modifier
    )
  }
}

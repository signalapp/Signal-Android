package org.thoughtcrime.securesms.util

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.Px

/**
 * Drawable that wraps another drawable but explicitly sets its intrinsic width and height as specified.
 */
class FixedSizeDrawable(
  drawable: Drawable,
  @Px private val width: Int,
  @Px private val height: Int
) : LayerDrawable(arrayOf(drawable)) {
  override fun getIntrinsicHeight(): Int = width
  override fun getIntrinsicWidth(): Int = height
}

fun Drawable.withFixedSize(@Px width: Int, @Px height: Int) = FixedSizeDrawable(this, width, height)
fun Drawable.withFixedSize(@Px size: Int) = withFixedSize(size, size)

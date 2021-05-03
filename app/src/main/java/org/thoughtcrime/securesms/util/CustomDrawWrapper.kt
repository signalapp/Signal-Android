package org.thoughtcrime.securesms.util

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

/**
 * Class which wraps a given drawable to perform some custom drawing / masking / whatever.
 *
 * We extend LayerDrawable here to take advantage of it's overrides and mechanisms, but explicitly
 * abstract out the draw method to allow overrides to do whatever they want.
 */
private class CustomDrawWrapper(
  private val wrapped: Drawable,
  private val drawFn: (wrapped: Drawable, canvas: Canvas) -> Unit
) : LayerDrawable(arrayOf(wrapped)) {
  override fun draw(canvas: Canvas) {
    drawFn(wrapped, canvas)
  }
}

fun Drawable.customizeOnDraw(customDrawFn: (wrapped: Drawable, canvas: Canvas) -> Unit): Drawable {
  return CustomDrawWrapper(this, customDrawFn)
}

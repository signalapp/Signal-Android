package org.thoughtcrime.securesms

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.ColorInt
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * BitmapTransformation which overlays the given bitmap with the given color.
 */
class OverlayTransformation(
  @ColorInt private val color: Int
) : BitmapTransformation() {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("${OverlayTransformation::class.java.name}$color".toByteArray(CHARSET))
  }

  override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
    val outBitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    canvas.drawBitmap(toTransform, 0f, 0f, null)
    canvas.drawColor(color)

    return outBitmap
  }
}

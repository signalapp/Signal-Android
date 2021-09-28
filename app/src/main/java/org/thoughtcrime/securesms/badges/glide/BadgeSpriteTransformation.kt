package org.thoughtcrime.securesms.badges.glide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.lang.IllegalArgumentException
import java.security.MessageDigest

/**
 * Cuts out the badge of the requested size from the sprite sheet.
 */
class BadgeSpriteTransformation(
  private val size: Size,
  private val density: String,
  private val isDarkTheme: Boolean
) : BitmapTransformation() {

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("BadgeSpriteTransformation(${size.code},$density,$isDarkTheme)".toByteArray(CHARSET))
  }

  override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
    val outBitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)
    val inBounds = getInBounds(density, size, isDarkTheme)
    val outBounds = Rect(0, 0, outWidth, outHeight)

    canvas.drawBitmap(toTransform, inBounds, outBounds, null)

    return outBitmap
  }

  enum class Size(val code: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    XLARGE("xlarge");

    companion object {
      fun fromInteger(integer: Int): Size {
        return when (integer) {
          0 -> SMALL
          1 -> MEDIUM
          2 -> LARGE
          3 -> XLARGE
          else -> LARGE
        }
      }
    }
  }

  companion object {
    private const val PADDING = 1

    @VisibleForTesting
    fun getInBounds(density: String, size: Size, isDarkTheme: Boolean): Rect {
      val scaleFactor: Int = when (density) {
        "ldpi" -> 75
        "mdpi" -> 100
        "hdpi" -> 150
        "xhdpi" -> 200
        "xxhdpi" -> 300
        "xxxhdpi" -> 400
        else -> throw IllegalArgumentException("Unexpected density $density")
      }

      val smallLength = 8 * scaleFactor / 100
      val mediumLength = 12 * scaleFactor / 100
      val largeLength = 18 * scaleFactor / 100
      val xlargeLength = 80 * scaleFactor / 100

      val sideLength: Int = when (size) {
        Size.SMALL -> smallLength
        Size.MEDIUM -> mediumLength
        Size.LARGE -> largeLength
        Size.XLARGE -> xlargeLength
      }

      val lightOffset: Int = when (size) {
        Size.LARGE -> PADDING
        Size.MEDIUM -> (largeLength + PADDING * 2) * 2 + PADDING
        Size.SMALL -> (largeLength + PADDING * 2) * 2 + (mediumLength + PADDING * 2) * 2 + PADDING
        Size.XLARGE -> (largeLength + PADDING * 2) * 2 + (mediumLength + PADDING * 2) * 2 + (smallLength + PADDING * 2) * 2 + PADDING
      }

      val darkOffset = if (isDarkTheme) {
        when (size) {
          Size.XLARGE -> 0
          else -> sideLength + PADDING * 2
        }
      } else {
        0
      }

      return Rect(
        lightOffset + darkOffset,
        PADDING,
        lightOffset + darkOffset + sideLength,
        sideLength + PADDING
      )
    }
  }
}

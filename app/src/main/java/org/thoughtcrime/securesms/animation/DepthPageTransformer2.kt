package org.thoughtcrime.securesms.animation

import android.view.View
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2

private const val MIN_SCALE = 0.75f

/**
 * Lifted from https://developer.android.com/develop/ui/views/animations/screen-slide-2#depth-page
 */
@RequiresApi(21)
class DepthPageTransformer2 : ViewPager2.PageTransformer {

  override fun transformPage(view: View, position: Float) {
    view.apply {
      val pageWidth = width
      when {
        position < -1 -> alpha = 0f
        position <= 0 -> {
          alpha = 1f
          translationX = 0f
          translationZ = 0f
          scaleX = 1f
          scaleY = 1f
        }
        position <= 1 -> {
          alpha = 1 - position

          translationX = pageWidth * -position
          translationZ = -1f

          val scaleFactor = (MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position)))
          scaleX = scaleFactor
          scaleY = scaleFactor
        }
        else -> alpha = 0f
      }
    }
  }
}

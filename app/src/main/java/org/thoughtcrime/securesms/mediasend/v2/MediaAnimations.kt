package org.thoughtcrime.securesms.mediasend.v2

import android.view.animation.Interpolator
import androidx.core.graphics.PathParser
import androidx.core.view.animation.PathInterpolatorCompat

object MediaAnimations {
  /**
   * Fast-In-Extra-Slow-Out Interpolator
   */
  @JvmStatic
  val interpolator: Interpolator = PathInterpolatorCompat.create(PathParser.createPathFromPathData("M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1"))
}

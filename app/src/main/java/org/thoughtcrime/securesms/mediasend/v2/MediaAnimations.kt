package org.thoughtcrime.securesms.mediasend.v2

import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import org.thoughtcrime.securesms.util.createDefaultCubicBezierInterpolator

object MediaAnimations {
  /**
   * Fast-In-Extra-Slow-Out Interpolator
   */
  @JvmStatic
  val interpolator: Interpolator = createDefaultCubicBezierInterpolator()

  val toolIconInterpolator = LinearInterpolator()
}

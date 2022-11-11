package org.thoughtcrime.securesms.util

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.os.Build
import android.provider.Settings

fun ContentResolver.areSystemAnimationsDisabled(): Boolean {
  return if (Build.VERSION.SDK_INT >= 26) {
    !ValueAnimator.areAnimatorsEnabled()
  } else {
    val durationScale = Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
    val transitionScale = Settings.Global.getFloat(this, Settings.Global.TRANSITION_ANIMATION_SCALE, 1.0f)

    !(durationScale > 0f && transitionScale > 0f)
  }
}

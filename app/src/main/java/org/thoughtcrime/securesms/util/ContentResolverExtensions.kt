package org.thoughtcrime.securesms.util

import android.content.ContentResolver
import android.provider.Settings

fun ContentResolver.areSystemAnimationsDisabled(): Boolean {
  val durationScale = Settings.System.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE)
  val transitionScale = Settings.System.getFloat(this, Settings.Global.TRANSITION_ANIMATION_SCALE)

  return !(durationScale > 0f && transitionScale > 0f)
}

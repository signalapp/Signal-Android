package org.thoughtcrime.securesms.components.recyclerview

import android.view.View

/**
 * Animates in and out a given view. This is intended to be used to show and hide a toolbar shadow,
 * but makes no restrictions in this manner.
 */
open class ToolbarShadowAnimationHelper(private val toolbarShadow: View) : OnScrollAnimationHelper() {

  override fun show(duration: Long) {
    toolbarShadow.animate()
      .setDuration(duration)
      .alpha(1f)
  }

  override fun hide(duration: Long) {
    toolbarShadow.animate()
      .setDuration(duration)
      .alpha(0f)
  }
}

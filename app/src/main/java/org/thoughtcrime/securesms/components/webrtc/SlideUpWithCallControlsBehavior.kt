/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.Barrier
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.views.SlideUpWithDependencyBehavior
import kotlin.math.max

/**
 * Coordinator Layout Behavior which allows us to "pin" UI Elements to the top of the controls sheet.
 */
class SlideUpWithCallControlsBehavior(
  context: Context,
  attributeSet: AttributeSet?
) : SlideUpWithDependencyBehavior(context, attributeSet, offsetY = 0f) {

  private var minTranslationY: Float = 0f

  var onTopOfControlsChangedListener: OnTopOfControlsChangedListener? = null

  override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
    super.onDependentViewChanged(parent, child, dependency)

    val bottomSheetBehavior = (dependency.layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior<*>
    val slideOffset = bottomSheetBehavior.calculateSlideOffset()
    if (slideOffset == 0f) {
      minTranslationY = child.translationY
    } else {
      child.translationY = max(child.translationY, minTranslationY)
    }

    emitViewChanged(child)
    return true
  }

  override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
    emitViewChanged(child)
    return false
  }

  override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
    return dependency.id == R.id.call_controls_info_parent
  }

  private fun emitViewChanged(child: View) {
    val barrier = child.findViewById<Barrier>(R.id.call_screen_above_controls_barrier)
    onTopOfControlsChangedListener?.onTopOfControlsChanged(barrier.bottom + child.translationY.toInt())
  }

  interface OnTopOfControlsChangedListener {
    fun onTopOfControlsChanged(topOfControls: Int)
  }
}

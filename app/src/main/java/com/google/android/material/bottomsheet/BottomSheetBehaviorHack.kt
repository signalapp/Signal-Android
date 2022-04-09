package com.google.android.material.bottomsheet

import android.view.View
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/**
 * Manually adjust the nested scrolling child for a given [BottomSheetBehavior].
 */
object BottomSheetBehaviorHack {
  fun setNestedScrollingChild(behavior: BottomSheetBehavior<FrameLayout>, view: View) {
    behavior.nestedScrollingChildRef = WeakReference(view)
  }
}

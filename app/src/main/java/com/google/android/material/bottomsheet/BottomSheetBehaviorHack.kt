package com.google.android.material.bottomsheet

import android.view.View
import java.lang.ref.WeakReference

/**
 * Manually adjust the nested scrolling child for a given [BottomSheetBehavior].
 */
object BottomSheetBehaviorHack {
  fun <T : View> setNestedScrollingChild(behavior: BottomSheetBehavior<T>, view: View) {
    behavior.nestedScrollingChildRef = WeakReference(view)
  }
}

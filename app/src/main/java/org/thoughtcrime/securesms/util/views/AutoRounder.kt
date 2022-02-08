package org.thoughtcrime.securesms.util.views

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.util.Consumer

/**
 * Given a view and a corner radius set callback, calculate the appropriate radius to
 * make the view have fully rounded sides (height/2).
 */
class AutoRounder<T : View> private constructor(private val view: T, private val setRadius: Consumer<Float>) : ViewTreeObserver.OnGlobalLayoutListener {
  override fun onGlobalLayout() {
    if (view.height > 0) {
      setRadius.accept(view.height.toFloat() / 2f)
      view.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }
  }

  companion object {
    @JvmStatic
    fun <VIEW : View> autoSetCorners(view: VIEW, setRadius: Consumer<Float>) {
      view.viewTreeObserver.addOnGlobalLayoutListener(AutoRounder(view, setRadius))
    }
  }
}

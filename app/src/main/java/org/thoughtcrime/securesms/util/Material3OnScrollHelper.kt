package org.thoughtcrime.securesms.util

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Sets the view's isActivated state when the content of the attached recycler can scroll up.
 * This can be used to appropriately tint toolbar backgrounds. Also can emit the state change
 * for other purposes.
 */
class Material3OnScrollHelper(
  private val views: List<View>,
  private val viewStubs: List<Stub<out View>> = emptyList(),
  private val onActiveStateChanged: (Boolean) -> Unit
) : RecyclerView.OnScrollListener() {

  constructor(activity: Activity, views: List<View>, viewStubs: List<Stub<out View>>) : this(views, viewStubs, { updateStatusBarColor(activity, it) })

  constructor(activity: Activity, view: View) : this(listOf(view), emptyList(), { updateStatusBarColor(activity, it) })

  private var active: Boolean? = null

  override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
    updateActiveState(recyclerView.canScrollVertically(-1))
  }

  private fun updateActiveState(isActive: Boolean) {
    if (active == isActive) {
      return
    }

    active = isActive

    views.forEach { it.isActivated = isActive }
    viewStubs.filter { it.resolved() }.forEach { it.get().isActivated = isActive }

    onActiveStateChanged(isActive)
  }

  companion object {
    fun updateStatusBarColor(activity: Activity, isActive: Boolean) {
      if (Build.VERSION.SDK_INT > 21) {
        if (isActive) {
          WindowUtil.setStatusBarColor(activity.window, ContextCompat.getColor(activity, R.color.signal_colorSurface2))
        } else {
          WindowUtil.setStatusBarColor(activity.window, ContextCompat.getColor(activity, R.color.signal_colorBackground))
        }
      }
    }
  }
}

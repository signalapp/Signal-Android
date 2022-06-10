package org.thoughtcrime.securesms.util

import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Sets the view's isActivated state when the content of the attached recycler can scroll up.
 * This can be used to appropriately tint toolbar backgrounds. Also can emit the state change
 * for other purposes.
 */
open class Material3OnScrollHelper(
  private val activity: Activity,
  private val views: List<View>,
  private val viewStubs: List<Stub<out View>> = emptyList()
) {

  open val activeColorRes: Int = R.color.signal_colorSurface2
  open val inactiveColorRes: Int = R.color.signal_colorBackground

  constructor(activity: Activity, view: View) : this(activity, listOf(view), emptyList())

  private var animator: ValueAnimator? = null
  private var active: Boolean? = null
  private val scrollListener = OnScrollListener()

  fun attach(recyclerView: RecyclerView) {
    recyclerView.addOnScrollListener(scrollListener)
    scrollListener.onScrolled(recyclerView, 0, 0)
  }

  /**
   * Cancels any currently running scroll animation and sets the color immediately.
   */
  fun setColorImmediate() {
    if (active == null) {
      return
    }

    animator?.cancel()
    setColor(ContextCompat.getColor(activity, if (active == true) activeColorRes else inactiveColorRes))
  }

  private fun updateActiveState(isActive: Boolean) {
    if (active == isActive) {
      return
    }

    val hadActiveState = active != null
    active = isActive

    views.forEach { it.isActivated = isActive }
    viewStubs.filter { it.resolved() }.forEach { it.get().isActivated = isActive }

    if (animator?.isRunning == true) {
      animator?.reverse()
    } else {
      val startColor = ContextCompat.getColor(activity, if (isActive) inactiveColorRes else activeColorRes)
      val endColor = ContextCompat.getColor(activity, if (isActive) activeColorRes else inactiveColorRes)

      if (hadActiveState) {
        animator = ValueAnimator.ofObject(ArgbEvaluatorCompat(), startColor, endColor).apply {
          duration = 200
          addUpdateListener { animator ->
            setColor(animator.animatedValue as Int)
          }
          start()
        }
      } else {
        setColorImmediate()
      }
    }
  }

  private fun setColor(@ColorInt color: Int) {
    WindowUtil.setStatusBarColor(activity.window, color)
    views.forEach { it.setBackgroundColor(color) }
    viewStubs.filter { it.resolved() }.forEach { it.get().setBackgroundColor(color) }
  }

  private inner class OnScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      updateActiveState(recyclerView.canScrollVertically(-1))
    }
  }
}

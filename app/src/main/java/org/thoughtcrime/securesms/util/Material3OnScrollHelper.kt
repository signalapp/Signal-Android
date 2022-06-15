package org.thoughtcrime.securesms.util

import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
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

  /**
   * A pair of colors tied to a specific state.
   */
  data class ColorSet(
    @ColorRes val toolbarColorRes: Int,
    @ColorRes val statusBarColorRes: Int
  ) {
    constructor(@ColorRes color: Int) : this(color, color)
  }

  open val activeColorSet: ColorSet = ColorSet(R.color.signal_colorSurface2)
  open val inactiveColorSet: ColorSet = ColorSet(R.color.signal_colorBackground)

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
    val colorSet = if (active == true) activeColorSet else inactiveColorSet
    setToolbarColor(ContextCompat.getColor(activity, colorSet.toolbarColorRes))
    setStatusBarColor(ContextCompat.getColor(activity, colorSet.statusBarColorRes))
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
      val startColorSet = if (isActive) inactiveColorSet else activeColorSet
      val endColorSet = if (isActive) activeColorSet else inactiveColorSet

      if (hadActiveState) {
        val startToolbarColor = ContextCompat.getColor(activity, startColorSet.toolbarColorRes)
        val endToolbarColor = ContextCompat.getColor(activity, endColorSet.toolbarColorRes)
        val startStatusBarColor = ContextCompat.getColor(activity, startColorSet.statusBarColorRes)
        val endStatusBarColor = ContextCompat.getColor(activity, endColorSet.statusBarColorRes)

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 200
          addUpdateListener {
            setToolbarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startToolbarColor, endToolbarColor))
            setStatusBarColor(ArgbEvaluatorCompat.getInstance().evaluate(it.animatedFraction, startStatusBarColor, endStatusBarColor))
          }
          start()
        }
      } else {
        setColorImmediate()
      }
    }
  }

  private fun setToolbarColor(@ColorInt color: Int) {
    views.forEach { it.setBackgroundColor(color) }
    viewStubs.filter { it.resolved() }.forEach { it.get().setBackgroundColor(color) }
  }

  private fun setStatusBarColor(@ColorInt color: Int) {
    WindowUtil.setStatusBarColor(activity.window, color)
  }

  private inner class OnScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      updateActiveState(recyclerView.canScrollVertically(-1))
    }
  }
}

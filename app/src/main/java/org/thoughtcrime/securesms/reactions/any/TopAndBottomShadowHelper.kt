package org.thoughtcrime.securesms.reactions.any

import android.view.View
import androidx.recyclerview.widget.RecyclerView

private const val DURATION: Long = 250L

/**
 * Hide and show top and bottom shadows based on list scrolling ability.
 */
class TopAndBottomShadowHelper(private val toolbarShadow: View, private val bottomToolbarShadow: View) : RecyclerView.OnScrollListener() {
  private var lastAnimationState = AnimationState.NONE

  override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
    val newAnimationState = getAnimationState(recyclerView)

    if (newAnimationState == lastAnimationState) {
      return
    }

    when (newAnimationState) {
      AnimationState.NONE -> throw AssertionError()
      AnimationState.HIDE_TOP_AND_HIDE_BOTTOM -> hide(toolbarShadow, bottomToolbarShadow)
      AnimationState.HIDE_TOP_AND_SHOW_BOTTOM -> {
        hide(toolbarShadow)
        show(bottomToolbarShadow)
      }
      AnimationState.SHOW_TOP_AND_HIDE_BOTTOM -> {
        show(toolbarShadow)
        hide(bottomToolbarShadow)
      }
      AnimationState.SHOW_TOP_AND_SHOW_BOTTOM -> show(toolbarShadow, bottomToolbarShadow)
    }

    lastAnimationState = newAnimationState
  }

  private fun getAnimationState(recyclerView: RecyclerView): AnimationState {
    val canScrollDown = recyclerView.canScrollVertically(1)
    val canScrollUp = recyclerView.canScrollVertically(-1)

    return if (!canScrollDown && !canScrollUp) {
      AnimationState.HIDE_TOP_AND_HIDE_BOTTOM
    } else if (canScrollDown && !canScrollUp) {
      AnimationState.HIDE_TOP_AND_SHOW_BOTTOM
    } else if (!canScrollDown && canScrollUp) {
      AnimationState.SHOW_TOP_AND_HIDE_BOTTOM
    } else {
      AnimationState.SHOW_TOP_AND_SHOW_BOTTOM
    }
  }

  private fun show(vararg views: View) {
    views.forEach {
      it.animate()
        .setDuration(DURATION)
        .alpha(1f)
    }
  }

  private fun hide(vararg views: View) {
    views.forEach {
      it.animate()
        .setDuration(DURATION)
        .alpha(0f)
    }
  }

  enum class AnimationState {
    NONE,
    HIDE_TOP_AND_HIDE_BOTTOM,
    HIDE_TOP_AND_SHOW_BOTTOM,
    SHOW_TOP_AND_HIDE_BOTTOM,
    SHOW_TOP_AND_SHOW_BOTTOM
  }
}

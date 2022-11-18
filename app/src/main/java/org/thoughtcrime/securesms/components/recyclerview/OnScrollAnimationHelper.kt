package org.thoughtcrime.securesms.components.recyclerview

import androidx.recyclerview.widget.RecyclerView

/**
 * Allows implementor to trigger an animation when the attached recyclerview is
 * scrolled.
 */
abstract class OnScrollAnimationHelper : RecyclerView.OnScrollListener() {
  private var lastAnimationState = AnimationState.NONE

  protected open val duration: Long = 250L

  override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
    val newAnimationState = getAnimationState(recyclerView)

    if (newAnimationState == lastAnimationState) {
      return
    }

    if (lastAnimationState == AnimationState.NONE) {
      setImmediateState(recyclerView)
      return
    }

    when (newAnimationState) {
      AnimationState.NONE -> throw AssertionError()
      AnimationState.HIDE -> hide(duration)
      AnimationState.SHOW -> show(duration)
    }

    lastAnimationState = newAnimationState
  }

  fun setImmediateState(recyclerView: RecyclerView) {
    val newAnimationState = getAnimationState(recyclerView)

    when (newAnimationState) {
      AnimationState.NONE -> throw AssertionError()
      AnimationState.HIDE -> hide(0L)
      AnimationState.SHOW -> show(0L)
    }

    lastAnimationState = newAnimationState
  }

  protected open fun getAnimationState(recyclerView: RecyclerView): AnimationState {
    return if (recyclerView.canScrollVertically(-1)) AnimationState.SHOW else AnimationState.HIDE
  }

  /**
   * Fired when the RecyclerView is able to be scrolled up
   */
  protected abstract fun show(duration: Long)

  /**
   * Fired when the RecyclerView is not able to be scrolled up
   */
  protected abstract fun hide(duration: Long)

  enum class AnimationState {
    NONE,
    HIDE,
    SHOW
  }
}

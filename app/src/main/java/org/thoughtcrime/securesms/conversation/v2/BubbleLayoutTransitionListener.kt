package org.thoughtcrime.securesms.conversation.v2

import android.animation.Animator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView

class BubbleLayoutTransitionListener(
  recyclerView: RecyclerView
) : DefaultLifecycleObserver {

  private val layoutTransition = LayoutTransition()
  private val transitionListener = TransitionListener(recyclerView)

  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)
    layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING).addListener(transitionListener)
  }

  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)
    layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING).removeListener(transitionListener)
  }

  private class TransitionListener(recyclerView: RecyclerView) : Animator.AnimatorListener {
    private val animator = ValueAnimator.ofFloat(0f, 1f)

    init {
      animator.addUpdateListener { recyclerView.invalidate() }
      animator.duration = 100L
    }

    override fun onAnimationStart(animation: Animator) {
      animator.start()
    }

    override fun onAnimationEnd(animation: Animator) {
      animator.end()
    }

    override fun onAnimationCancel(animation: Animator) = Unit

    override fun onAnimationRepeat(animation: Animator) = Unit
  }
}

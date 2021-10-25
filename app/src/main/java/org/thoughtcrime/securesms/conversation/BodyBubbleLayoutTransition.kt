package org.thoughtcrime.securesms.conversation

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import java.lang.IllegalStateException

class BodyBubbleLayoutTransition(bodyBubble: ConversationItemBodyBubble) : LayoutTransition() {

  private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)

  init {
    disableTransitionType(APPEARING)
    disableTransitionType(DISAPPEARING)
    disableTransitionType(CHANGE_APPEARING)
    disableTransitionType(CHANGING)

    setDuration(100L)

    animator.duration = getAnimator(CHANGE_DISAPPEARING).duration
    animator.addUpdateListener {
      val parentRecycler: RecyclerView? = bodyBubble.parent.parent as? RecyclerView

      try {
        parentRecycler?.invalidate()
      } catch (e: IllegalStateException) {
        // In scroll or layout. Skip this frame.
      }
    }

    getAnimator(CHANGE_DISAPPEARING).addListener(
      onStart = {
        animator.start()
      },
      onEnd = {
        animator.end()
      }
    )
  }
}

package org.thoughtcrime.securesms.conversation.mutiselect

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationAdapter

/**
 * Class for managing the triggering of item animations (here in the form of decoration redraws) whenever
 * there is a "selection" edge detected.
 *
 * Can be expanded upon in the future to animate other things, such as message sends.
 */
class MultiselectItemAnimator(
  private val isInMultiSelectMode: () -> Boolean,
  private val isLoadingInitialContent: () -> Boolean,
  private val isParentFilled: () -> Boolean
) : RecyclerView.ItemAnimator() {

  private enum class Operation {
    ADD,
    CHANGE
  }

  private val pendingSlideAnimations: MutableSet<RecyclerView.ViewHolder> = mutableSetOf()
  private var pendingTypingViewSlideOut: RecyclerView.ViewHolder? = null

  private val slideAnimations: MutableMap<RecyclerView.ViewHolder, ValueAnimator> = mutableMapOf()

  override fun animateDisappearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo?): Boolean {
    if (viewHolder is ConversationAdapter.HeaderViewHolder && pendingTypingViewSlideOut == null) {
      pendingTypingViewSlideOut = viewHolder
      dispatchAnimationStarted(viewHolder)
      return true
    }

    dispatchAnimationFinished(viewHolder)
    return false
  }

  override fun animateAppearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
    if (viewHolder.absoluteAdapterPosition > 1) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    return animateSlide(viewHolder, preLayoutInfo, postLayoutInfo, Operation.ADD)
  }

  private fun animateSlide(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo, operation: Operation): Boolean {
    if (isInMultiSelectMode() || isLoadingInitialContent()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (operation == Operation.CHANGE && !isParentFilled() || slideAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (slideAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    val translationY = if (preLayoutInfo == null) {
      postLayoutInfo.bottom - postLayoutInfo.top
    } else {
      preLayoutInfo.top - postLayoutInfo.top
    }.toFloat()

    if (translationY == 0f) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    viewHolder.itemView.translationY = translationY

    pendingSlideAnimations.add(viewHolder)
    dispatchAnimationStarted(viewHolder)
    return true
  }

  override fun animatePersistence(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    val isInMultiSelectMode = isInMultiSelectMode()
    return if (!isInMultiSelectMode) {
      if (pendingSlideAnimations.contains(viewHolder) || slideAnimations.containsKey(viewHolder)) {
        dispatchAnimationFinished(viewHolder)
        false
      } else {
        animateSlide(viewHolder, preLayoutInfo, postLayoutInfo, Operation.CHANGE)
      }
    } else {
      dispatchAnimationFinished(viewHolder)
      false
    }
  }

  override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    if (oldHolder != newHolder) {
      dispatchAnimationFinished(oldHolder)
    }

    return animatePersistence(newHolder, preLayoutInfo, postLayoutInfo)
  }

  override fun runPendingAnimations() {
    runPendingSlideAnimations()
    runPendingSlideOutAnimation()
  }

  private fun runPendingSlideAnimations() {
    for (viewHolder in pendingSlideAnimations) {
      val animator = ObjectAnimator.ofFloat(viewHolder.itemView, "translationY", 0f)
      slideAnimations[viewHolder]?.cancel()
      slideAnimations[viewHolder] = animator
      animator.duration = 150L
      animator.addUpdateListener {
        (viewHolder.itemView.parent as RecyclerView?)?.invalidate()
      }
      animator.doOnEnd {
        viewHolder.itemView.translationY = 0f
        slideAnimations.remove(viewHolder)
        dispatchAnimationFinished(viewHolder)
        dispatchFinishedWhenDone()
      }
      animator.start()
    }

    pendingSlideAnimations.clear()
  }

  private fun runPendingSlideOutAnimation() {
    val viewHolder = pendingTypingViewSlideOut
    if (viewHolder != null) {
      pendingTypingViewSlideOut = null
      slideAnimations[viewHolder]?.cancel()

      val animator = ObjectAnimator.ofFloat(viewHolder.itemView, "translationY", viewHolder.itemView.height.toFloat())

      slideAnimations[viewHolder] = animator
      animator.duration = 150L
      animator.addUpdateListener {
        (viewHolder.itemView.parent as RecyclerView?)?.invalidate()
      }
      animator.doOnEnd {
        viewHolder.itemView.translationY = 0f
        slideAnimations.remove(viewHolder)
        dispatchAnimationFinished(viewHolder)
        dispatchFinishedWhenDone()
      }
      animator.start()
    }
  }

  override fun endAnimation(item: RecyclerView.ViewHolder) {
    endSlideAnimation(item)
  }

  override fun endAnimations() {
    endSlideAnimations()
    dispatchAnimationsFinished()
  }

  override fun isRunning(): Boolean {
    return slideAnimations.values.any { it.isRunning }
  }

  override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
    val parent = (viewHolder.itemView.parent as? RecyclerView)
    parent?.post { parent.invalidate() }
  }

  private fun endSlideAnimation(item: RecyclerView.ViewHolder) {
    slideAnimations[item]?.cancel()
  }

  fun endSlideAnimations() {
    slideAnimations.values.forEach { it.cancel() }
  }

  private fun dispatchFinishedWhenDone() {
    if (!isRunning) {
      dispatchAnimationsFinished()
    }
  }
}

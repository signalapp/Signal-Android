package org.thoughtcrime.securesms.conversation.mutiselect

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView

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

  private data class SlideInfo(
    val viewHolder: RecyclerView.ViewHolder,
    val operation: Operation
  )

  private enum class Operation {
    ADD,
    CHANGE
  }

  private val pendingSlideAnimations: MutableSet<SlideInfo> = mutableSetOf()

  private val slideAnimations: MutableMap<SlideInfo, ValueAnimator> = mutableMapOf()

  override fun animateDisappearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo?): Boolean {
    dispatchAnimationFinished(viewHolder)
    return false
  }

  override fun animateAppearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
    return animateSlide(viewHolder, preLayoutInfo, postLayoutInfo, Operation.ADD)
  }

  private fun animateSlide(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo, operation: Operation): Boolean {
    if (isInMultiSelectMode() || isLoadingInitialContent()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    if (operation == Operation.CHANGE && !isParentFilled()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    val translationY = if (preLayoutInfo == null) {
      postLayoutInfo.bottom - postLayoutInfo.top
    } else {
      preLayoutInfo.top - postLayoutInfo.top
    }.toFloat()

    viewHolder.itemView.translationY = translationY
    val slideInfo = SlideInfo(viewHolder, operation)

    if (slideAnimations.filterKeys { slideInfo.viewHolder == viewHolder }.isNotEmpty()) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    pendingSlideAnimations.add(slideInfo)
    dispatchAnimationStarted(viewHolder)
    return true
  }

  override fun animatePersistence(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    dispatchAnimationFinished(viewHolder)
    return false
  }

  override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    if (oldHolder != newHolder) {
      dispatchAnimationFinished(oldHolder)
    }

    val isInMultiSelectMode = isInMultiSelectMode()
    return if (!isInMultiSelectMode) {
      if (preLayoutInfo.top == postLayoutInfo.top) {
        dispatchAnimationFinished(newHolder)
        false
      } else {
        animateSlide(newHolder, preLayoutInfo, postLayoutInfo, Operation.CHANGE)
      }
    } else {
      dispatchAnimationFinished(newHolder)
      false
    }
  }

  override fun runPendingAnimations() {
    runPendingSlideAnimations()
  }

  private fun runPendingSlideAnimations() {
    for (slideInfo in pendingSlideAnimations) {
      val animator = ObjectAnimator.ofFloat(slideInfo.viewHolder.itemView, "translationY", 0f)
      slideAnimations[slideInfo] = animator
      animator.duration = 150L
      animator.addUpdateListener {
        (slideInfo.viewHolder.itemView.parent as RecyclerView?)?.invalidate()
      }
      animator.doOnEnd {
        dispatchAnimationFinished(slideInfo.viewHolder)
        slideAnimations.remove(slideInfo)
      }
      animator.start()
    }

    pendingSlideAnimations.clear()
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
    val selections = slideAnimations.filter { (k, _) -> k.viewHolder == item }
    selections.forEach { (k, v) ->
      v.end()
      slideAnimations.remove(k)
    }
  }

  fun endSlideAnimations() {
    slideAnimations.values.forEach { it.end() }
    slideAnimations.clear()
  }
}

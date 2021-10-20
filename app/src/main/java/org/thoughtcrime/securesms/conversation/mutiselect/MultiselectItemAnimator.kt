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
  private val isPartSelected: (MultiselectPart) -> Boolean,
  private val isParentFilled: () -> Boolean
) : RecyclerView.ItemAnimator() {

  private data class Selection(
    val multiselectPart: MultiselectPart,
    val viewHolder: RecyclerView.ViewHolder
  )

  private data class SlideInfo(
    val viewHolder: RecyclerView.ViewHolder,
    val operation: Operation
  )

  private enum class Operation {
    ADD,
    CHANGE
  }

  var isInitialMultiSelectAnimation: Boolean = true
    private set

  private val selected: MutableSet<MultiselectPart> = mutableSetOf()

  private val pendingSelectedAnimations: MutableSet<Selection> = mutableSetOf()
  private val pendingSlideAnimations: MutableSet<SlideInfo> = mutableSetOf()

  private val selectedAnimations: MutableMap<Selection, ValueAnimator> = mutableMapOf()
  private val slideAnimations: MutableMap<SlideInfo, ValueAnimator> = mutableMapOf()

  fun getSelectedProgressForPart(multiselectPart: MultiselectPart): Float {
    return if (pendingSelectedAnimations.any { it.multiselectPart == multiselectPart }) {
      0f
    } else {
      selectedAnimations.filter { it.key.multiselectPart == multiselectPart }.values.firstOrNull()?.animatedFraction ?: 1f
    }
  }

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
    if (!isInMultiSelectMode) {
      selected.clear()
      isInitialMultiSelectAnimation = true
      return if (preLayoutInfo.top == postLayoutInfo.top) {
        dispatchAnimationFinished(newHolder)
        false
      } else {
        animateSlide(newHolder, preLayoutInfo, postLayoutInfo, Operation.CHANGE)
      }
    }

    var isAnimationStarted = false
    val parts: MultiselectCollection? = (newHolder.itemView as? Multiselectable)?.conversationMessage?.multiselectCollection

    if (parts == null || parts.isExpired()) {
      dispatchAnimationFinished(newHolder)
      return false
    }

    parts.toSet().forEach { part ->
      val partIsSelected = isPartSelected(part)
      if (selected.contains(part) && !partIsSelected) {
        pendingSelectedAnimations.add(Selection(part, newHolder))
        selected.remove(part)
        isAnimationStarted = true
      } else if (!selected.contains(part) && partIsSelected) {
        pendingSelectedAnimations.add(Selection(part, newHolder))
        selected.add(part)
        isAnimationStarted = true
      } else if (isInitialMultiSelectAnimation) {
        pendingSelectedAnimations.add(Selection(part, newHolder))
        isAnimationStarted = true
      }
    }

    if (isAnimationStarted) {
      dispatchAnimationStarted(newHolder)
    } else {
      dispatchAnimationFinished(newHolder)
    }

    return isAnimationStarted
  }

  override fun runPendingAnimations() {
    runPendingSelectedAnimations()
    runPendingSlideAnimations()
  }

  private fun runPendingSlideAnimations() {
    for (slideInfo in pendingSlideAnimations) {
      val animator = ObjectAnimator.ofFloat(slideInfo.viewHolder.itemView, "translationY", 0f)
      slideAnimations[slideInfo] = animator
      animator.duration = 150L
      animator.addUpdateListener {
        (slideInfo.viewHolder.itemView.parent as RecyclerView?)?.invalidateItemDecorations()
      }
      animator.doOnEnd {
        dispatchAnimationFinished(slideInfo.viewHolder)
        slideAnimations.remove(slideInfo)
      }
      animator.start()
    }

    pendingSlideAnimations.clear()
  }

  private fun runPendingSelectedAnimations() {
    for (selection in pendingSelectedAnimations) {
      val animator = ValueAnimator.ofFloat(0f, 1f)
      selectedAnimations[selection] = animator
      animator.duration = 150L
      animator.addUpdateListener {
        (selection.viewHolder.itemView.parent as RecyclerView?)?.invalidateItemDecorations()
      }
      animator.doOnEnd {
        dispatchAnimationFinished(selection.viewHolder)
        selectedAnimations.remove(selection)
        isInitialMultiSelectAnimation = false
      }
      animator.start()
    }

    pendingSelectedAnimations.clear()
  }

  override fun endAnimation(item: RecyclerView.ViewHolder) {
    endSelectedAnimation(item)
    endSlideAnimation(item)
  }

  override fun endAnimations() {
    endSelectedAnimations()
    endSlideAnimations()
    dispatchAnimationsFinished()
  }

  override fun isRunning(): Boolean {
    return (selectedAnimations.values + slideAnimations.values).any { it.isRunning }
  }

  override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
    val parent = (viewHolder.itemView.parent as? RecyclerView)
    parent?.post { parent.invalidateItemDecorations() }
  }

  private fun endSelectedAnimation(item: RecyclerView.ViewHolder) {
    val selections = selectedAnimations.filter { (k, _) -> k.viewHolder == item }
    selections.forEach { (k, v) ->
      v.end()
      selectedAnimations.remove(k)
    }
  }

  private fun endSlideAnimation(item: RecyclerView.ViewHolder) {
    val selections = slideAnimations.filter { (k, _) -> k.viewHolder == item }
    selections.forEach { (k, v) ->
      v.end()
      slideAnimations.remove(k)
    }
  }

  fun endSelectedAnimations() {
    selectedAnimations.values.forEach { it.end() }
    selectedAnimations.clear()
  }

  fun endSlideAnimations() {
    slideAnimations.values.forEach { it.end() }
    slideAnimations.clear()
  }
}

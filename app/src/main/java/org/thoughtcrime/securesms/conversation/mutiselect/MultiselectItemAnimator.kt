package org.thoughtcrime.securesms.conversation.mutiselect

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
  private val isPartSelected: (MultiselectPart) -> Boolean
) : RecyclerView.ItemAnimator() {

  private data class Selection(
    val multiselectPart: MultiselectPart,
    val viewHolder: RecyclerView.ViewHolder
  )

  var isInitialAnimation: Boolean = true
    private set

  private val selected: MutableSet<MultiselectPart> = mutableSetOf()

  private val pendingSelectedAnimations: MutableSet<Selection> = mutableSetOf()

  private val selectedAnimations: MutableMap<Selection, ValueAnimator> = mutableMapOf()

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
    dispatchAnimationFinished(viewHolder)
    return false
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
      isInitialAnimation = true
      dispatchAnimationFinished(newHolder)
      return false
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
      } else if (isInitialAnimation) {
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
        isInitialAnimation = false
      }
      animator.start()
    }

    pendingSelectedAnimations.clear()
  }

  override fun endAnimation(item: RecyclerView.ViewHolder) {
    endSelectedAnimation(item)
  }

  override fun endAnimations() {
    endSelectedAnimations()
    dispatchAnimationsFinished()
  }

  override fun isRunning(): Boolean {
    return selectedAnimations.values.any { it.isRunning }
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

  fun endSelectedAnimations() {
    selectedAnimations.values.forEach { it.end() }
    selectedAnimations.clear()
  }
}

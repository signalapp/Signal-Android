/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Build
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.logging.Log

/**
 * Reactions item animator based on [org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator]
 */
class WebRtcReactionsItemAnimator : RecyclerView.ItemAnimator() {

  private data class TweeningInfo(
    val property: AnimatedProperty,
    val interpolator: Interpolator,
    val startValue: Float,
    val endValue: Float
  ) {
    private val range = endValue - startValue

    fun calculateCurrentValue(progress: Float): Float {
      val interpolatedProgress = interpolator.getInterpolation(progress)
      return startValue + (interpolatedProgress * range)
    }
  }

  private data class AnimationInfo(
    val sharedAnimator: ValueAnimator,
    val tweeningInfos: List<TweeningInfo>
  )

  private enum class AnimatedProperty {
    TRANSLATION_Y,
    ALPHA
  }

  private val accelerateInterpolator: Interpolator = AccelerateInterpolator()
  private val decelerateInterpolator: Interpolator = DecelerateInterpolator()

  private val pendingAnimations: MutableMap<RecyclerView.ViewHolder, List<TweeningInfo>> = mutableMapOf()
  private val activeAnimations: MutableMap<RecyclerView.ViewHolder, AnimationInfo> = mutableMapOf()

  override fun animateDisappearance(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo?): Boolean {
    if (!pendingAnimations.containsKey(viewHolder) &&
      !activeAnimations.containsKey(viewHolder)
    ) {
      val existingAnimations = pendingAnimations[viewHolder]?.toMutableList() ?: mutableListOf()

      val startingAlpha = when (viewHolder.layoutPosition) {
        WebRtcReactionsRecyclerAdapter.MAX_REACTION_NUMBER - 2 -> 0.7f
        WebRtcReactionsRecyclerAdapter.MAX_REACTION_NUMBER - 3 -> 0.9f
        else -> 1f
      }
      existingAnimations.add(TweeningInfo(AnimatedProperty.ALPHA, accelerateInterpolator, startingAlpha, 0f))
      existingAnimations.add(TweeningInfo(AnimatedProperty.TRANSLATION_Y, accelerateInterpolator, 0f, (preLayoutInfo.top - preLayoutInfo.bottom) / 2f))

      pendingAnimations[viewHolder] = existingAnimations
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
    val existingAnimations = pendingAnimations[viewHolder]?.toMutableList() ?: mutableListOf()
    existingAnimations.add(TweeningInfo(AnimatedProperty.ALPHA, accelerateInterpolator, 0f, 1f))
    pendingAnimations[viewHolder] = existingAnimations
    return animateSlideIn(viewHolder, preLayoutInfo, postLayoutInfo)
  }

  private fun animateSlideIn(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo?, postLayoutInfo: ItemHolderInfo): Boolean {
    if (activeAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      return false
    }

    val translationY = if (preLayoutInfo == null) {
      postLayoutInfo.bottom - postLayoutInfo.top
    } else {
      preLayoutInfo.top - postLayoutInfo.top
    }.toFloat()

    if (translationY == 0f) {
      viewHolder.itemView.translationY = 0f
      dispatchAnimationFinished(viewHolder)
      return false
    }

    viewHolder.itemView.translationY = translationY

    val existingAnimations = pendingAnimations[viewHolder]?.toMutableList() ?: mutableListOf()
    existingAnimations.add(TweeningInfo(AnimatedProperty.TRANSLATION_Y, decelerateInterpolator, translationY, 0f))
    pendingAnimations[viewHolder] = existingAnimations
    dispatchAnimationStarted(viewHolder)

    return true
  }

  override fun animatePersistence(viewHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    return if (pendingAnimations.contains(viewHolder) || activeAnimations.containsKey(viewHolder)) {
      dispatchAnimationFinished(viewHolder)
      false
    } else {
      animateSlideIn(viewHolder, preLayoutInfo, postLayoutInfo)
    }
  }

  override fun animateChange(oldHolder: RecyclerView.ViewHolder, newHolder: RecyclerView.ViewHolder, preLayoutInfo: ItemHolderInfo, postLayoutInfo: ItemHolderInfo): Boolean {
    if (oldHolder != newHolder) {
      dispatchAnimationFinished(oldHolder)
    }

    return animatePersistence(newHolder, preLayoutInfo, postLayoutInfo)
  }

  override fun runPendingAnimations() {
    Log.d(TAG, "Starting ${pendingAnimations.size} animations.")
    runPendingAnimationsInternal()
  }

  private fun runPendingAnimationsInternal() {
    activeAnimations.filter { pendingAnimations.containsKey(it.key) }
      .forEach {
        it.value.sharedAnimator.end()
        handleAnimationEnd(it.key)
      }
    val animator = ValueAnimator.ofFloat(0f, 1f)

    animator.duration = ANIMATION_DURATION
    animator.addUpdateListener {
      activeAnimationsByAnimator(it).forEach { (viewHolder, animationInfo) ->
        val itemView = viewHolder.itemView
        animationInfo.tweeningInfos.forEach { tween ->
          val currentValue = tween.calculateCurrentValue(it.animatedFraction)
          when (tween.property) {
            AnimatedProperty.TRANSLATION_Y -> itemView.translationY = currentValue
            AnimatedProperty.ALPHA -> {
              if (Build.VERSION.SDK_INT >= 29) {
                itemView.transitionAlpha = currentValue
              } else {
                itemView.alpha = currentValue
              }
            }
          }
        }
      }
    }

    animator.doOnEnd {
      activeAnimationsByAnimator(it)
        .forEach { (viewHolder, _) ->
          handleAnimationEnd(viewHolder)
        }
    }

    animator.start()

    activeAnimations.putAll(pendingAnimations.mapValues { AnimationInfo(animator, it.value) })

    pendingAnimations.clear()
  }

  private fun handleAnimationEnd(viewHolder: RecyclerView.ViewHolder) {
    viewHolder.itemView.translationY = 0f
    if (Build.VERSION.SDK_INT >= 29) {
      viewHolder.itemView.transitionAlpha = 1f
    } else {
      viewHolder.itemView.alpha = 1f
    }
    activeAnimations.remove(viewHolder)

    dispatchAnimationFinished(viewHolder)
    dispatchFinishedWhenDone()
  }

  override fun endAnimation(item: RecyclerView.ViewHolder) {
    endSlideAnimation(item)
  }

  override fun endAnimations() {
    endSlideAnimations()
    dispatchAnimationsFinished()
  }

  override fun isRunning(): Boolean {
    return activeAnimations.values.any { it.sharedAnimator.isRunning }
  }

  override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
    val parent = (viewHolder.itemView.parent as? RecyclerView)
    parent?.post { parent.invalidate() }
  }

  private fun endSlideAnimation(item: RecyclerView.ViewHolder) {
    activeAnimations[item]?.sharedAnimator?.cancel()
  }

  private fun endSlideAnimations() {
    activeAnimations.values.map { it.sharedAnimator }.forEach {
      it.cancel()
    }
  }

  private fun dispatchFinishedWhenDone() {
    if (!isRunning) {
      Log.d(TAG, "Finished running animations.")
      dispatchAnimationsFinished()
    }
  }

  private fun activeAnimationsByAnimator(it: Animator): Map<RecyclerView.ViewHolder, AnimationInfo> {
    return activeAnimations.filterValues { animationInfo: AnimationInfo -> animationInfo.sharedAnimator == it }
  }

  companion object {
    private val TAG = Log.tag(WebRtcReactionsItemAnimator::class.java)
    private const val ANIMATION_DURATION = 150L
  }
}

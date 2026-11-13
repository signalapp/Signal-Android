/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator

/**
 * Animates a bubble's bounds from where it was laid out to where it is laid out next, so that content changes on an
 * already-visible message (like losing its footer when a newer message is sent) resize the bubble smoothly.
 *
 * The animation is started from within the layout pass, so the very next frame is drawn from the old bounds.
 */
class V2BubbleBoundsAnimator(private val view: View) : View.OnLayoutChangeListener {

  private val from = Rect()
  private val to = Rect()
  private val laidOut = Rect()

  private var armed = false
  private var animator: ValueAnimator? = null

  init {
    view.addOnLayoutChangeListener(this)
  }

  /**
   * Animate the bounds change caused by the next layout, if there is one.
   */
  fun animateNextLayout() {
    armed = true
  }

  /**
   * Stop animating and leave the bounds to the next layout.
   */
  fun cancel() {
    armed = false
    animator?.cancel()
    animator = null
  }

  override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
    val running = animator?.isRunning == true
    if (!armed && !running) {
      return
    }

    armed = false
    laidOut.set(left, top, right, bottom)

    if (running && laidOut == to) {
      applyFraction(animator!!.animatedFraction)
      return
    }

    val start = Rect(oldLeft, oldTop, oldRight, oldBottom)
    animator?.cancel()
    animator = null

    if (start == laidOut || start.isEmpty) {
      return
    }

    from.set(start)
    to.set(laidOut)

    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = ConversationItemAnimator.SLIDE_DURATION
      interpolator = ConversationItemAnimator.SLIDE_INTERPOLATOR
      addUpdateListener { applyFraction(it.animatedFraction) }
      start()
    }

    applyFraction(0f)
  }

  private fun applyFraction(fraction: Float) {
    view.left = lerp(from.left, to.left, fraction)
    view.top = lerp(from.top, to.top, fraction)
    view.right = lerp(from.right, to.right, fraction)
    view.bottom = lerp(from.bottom, to.bottom, fraction)
  }

  private fun lerp(start: Int, end: Int, fraction: Float): Int {
    return start + ((end - start) * fraction).toInt()
  }
}

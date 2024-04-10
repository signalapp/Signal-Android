/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.animation.transitions

import android.animation.Animator
import android.animation.ObjectAnimator
import android.util.Property
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.transition.Transition
import androidx.transition.TransitionValues
import kotlin.math.min

abstract class CircleSquareImageViewTransition(private val toCircle: Boolean) : Transition() {
  companion object {
    private const val CIRCLE_RATIO = "CIRCLE_RATIO"

    class RadiusRatioProperty : Property<ImageView, Float>(Float::class.java, "circle_ratio") {
      private var ratio: Float = 0.0f

      override fun set(imageView: ImageView?, ratio: Float) {
        this.ratio = ratio

        imageView ?: return
        val drawable = imageView.drawable as? RoundedBitmapDrawable ?: return
        if (ratio > 0.95) {
          drawable.isCircular = true
        } else {
          drawable.cornerRadius = min(drawable.intrinsicWidth, drawable.intrinsicHeight) * ratio * 0.5f
        }
      }

      override fun get(imageView: ImageView?): Float = ratio
    }
  }

  override fun captureStartValues(transitionValues: TransitionValues) {
    if (transitionValues.view is ImageView) {
      transitionValues.values[CIRCLE_RATIO] = if (toCircle) 0f else 1f
    }
  }

  override fun captureEndValues(transitionValues: TransitionValues) {
    if (transitionValues.view is ImageView) {
      transitionValues.values[CIRCLE_RATIO] = if (toCircle) 1f else 0f
    }
  }

  override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
    startValues ?: return null
    endValues ?: return null

    val endImageView = endValues.view as ImageView
    val start = startValues.values[CIRCLE_RATIO] as Float
    val end = startValues.values[CIRCLE_RATIO] as Float

    return ObjectAnimator.ofFloat(endImageView, RadiusRatioProperty(), start, end)
  }
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.animation.LayoutTransition
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Helps manage layout transition state inside a RecyclerView.
 *
 * Because of how RecyclerViews scroll, we need to be very careful about LayoutTransition
 * usage inside of them. This class helps wrap up the pattern of finding and listening to
 * the scroll events of a parent recycler view, so that we don't need to manually wire
 * the scroll state in everywhere.
 */
class RecyclerViewParentTransitionController(
  private val child: ViewGroup,
  private val transition: LayoutTransition = LayoutTransition()
) : RecyclerView.OnScrollListener(), OnAttachStateChangeListener {

  private var recyclerViewParent: RecyclerView? = null

  override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
      child.layoutTransition = transition
    } else {
      child.layoutTransition = null
    }
  }

  override fun onViewAttachedToWindow(v: View) {
    val parent = findRecyclerParent()

    if (parent != null) {
      onScrollStateChanged(parent, parent.scrollState)
    }

    parent?.addOnScrollListener(this)
    recyclerViewParent = parent
  }

  override fun onViewDetachedFromWindow(v: View) {
    recyclerViewParent?.removeOnScrollListener(this)
    child.layoutTransition = null
  }

  private fun findRecyclerParent(): RecyclerView? {
    var target: ViewGroup? = child.parent as? ViewGroup
    while (target != null) {
      if (target is RecyclerView) {
        return target
      }

      target = target.parent as? ViewGroup
    }

    return null
  }
}

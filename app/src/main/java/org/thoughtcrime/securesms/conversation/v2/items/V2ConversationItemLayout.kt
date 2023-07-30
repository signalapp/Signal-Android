/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Base Conversation item layout. Gives consistent patterns for manipulating child
 * views.
 */
class V2ConversationItemLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  private var onMeasureListeners: Set<OnMeasureListener> = emptySet()

  /**
   * Set the onMeasureListener to be invoked by this view whenever onMeasure is called.
   */
  fun addOnMeasureListener(onMeasureListener: OnMeasureListener) {
    this.onMeasureListeners += onMeasureListener
  }

  fun removeOnMeasureListener(onMeasureListener: OnMeasureListener) {
    this.onMeasureListeners -= onMeasureListener
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    onMeasureListeners.forEach { it.onPreMeasure() }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val remeasure = onMeasureListeners.map { it.onPostMeasure() }.any { it }
    if (remeasure) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }

  interface OnMeasureListener {
    /**
     * Allows the view to be manipulated before super.onMeasure is called.
     */
    fun onPreMeasure()

    /**
     * Custom onMeasure implementation. Use this to position views and set padding
     * *after* an initial measure pass, and optionally invoke an additional measure pass.
     *
     * @return true if super.onMeasure should be called again, false otherwise.
     */
    fun onPostMeasure(): Boolean
  }
}

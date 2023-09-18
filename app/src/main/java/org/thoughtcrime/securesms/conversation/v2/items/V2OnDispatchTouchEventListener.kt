/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode

/**
 * Responsible for the shrink-and-return feel of conversation bubbles when the user
 * touches them.
 */
class V2OnDispatchTouchEventListener(
  private val conversationContext: V2ConversationContext,
  private val binding: V2ConversationItemTextOnlyBindingBridge
) : V2ConversationItemLayout.OnDispatchTouchEventListener {

  companion object {
    private const val LONG_PRESS_SCALE_FACTOR = 0.95f
    private const val SHRINK_BUBBLE_DELAY_MILLIS = 100L
  }

  private val viewsToPivot = listOfNotNull(
    binding.footerBackground,
    binding.footerDate,
    binding.footerExpiry,
    binding.deliveryStatus,
    binding.reactions
  )

  private val shrinkBubble = Runnable {
    binding.bodyWrapper.animate()
      .scaleX(LONG_PRESS_SCALE_FACTOR)
      .scaleY(LONG_PRESS_SCALE_FACTOR)
      .setUpdateListener {
        (binding.root.parent as? ViewGroup)?.invalidate()
      }

    viewsToPivot.forEach {
      it.animate()
        .scaleX(LONG_PRESS_SCALE_FACTOR)
        .scaleY(LONG_PRESS_SCALE_FACTOR)
    }
  }

  override fun onDispatchTouchEvent(view: View, motionEvent: MotionEvent) {
    if (conversationContext.displayMode is ConversationItemDisplayMode.Condensed) {
      return
    }

    viewsToPivot.forEach {
      val deltaX = it.x - binding.bodyWrapper.x
      val deltaY = it.y - binding.bodyWrapper.y

      it.pivotX = -(deltaX / 2f)
      it.pivotY = -(deltaY / 2f)
    }

    when (motionEvent.action) {
      MotionEvent.ACTION_DOWN -> view.handler.postDelayed(shrinkBubble, SHRINK_BUBBLE_DELAY_MILLIS)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        view.handler.removeCallbacks(shrinkBubble)
        (viewsToPivot + binding.bodyWrapper).forEach {
          it.animate()
            .scaleX(1f)
            .scaleY(1f)
        }
      }
    }
  }
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.view.View
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible

/**
 * Logical delegate for determining the footer position for a particular conversation item.
 */
class V2FooterPositionDelegate private constructor(
  private val isIncoming: Boolean,
  private val root: V2ConversationItemLayout,
  private val footerViews: List<View>,
  private val bodyContainer: View,
  private val body: EmojiTextView
) : V2ConversationItemLayout.OnMeasureListener {

  constructor(binding: V2ConversationItemTextOnlyBindingBridge) : this(
    binding.isIncoming,
    binding.root,
    listOfNotNull(binding.conversationItemFooterDate, binding.conversationItemDeliveryStatus, binding.conversationItemFooterExpiry),
    binding.conversationItemBodyWrapper,
    binding.conversationItemBody
  )

  private val gutters = 48.dp + 16.dp
  private val horizontalFooterPadding = root.context.resources.getDimensionPixelOffset(R.dimen.message_bubble_horizontal_padding)

  private var displayState: DisplayState = DisplayState.NONE

  override fun onPreMeasure() {
    displayTuckedIntoBody()
  }

  override fun onPostMeasure(): Boolean {
    val maxWidth = root.measuredWidth - gutters
    val lastLineWidth = body.lastLineWidth
    val footerWidth = footerViews.sumOf { it.measuredWidth }

    if (footerViews.all { !it.visible }) {
      return false
    }

    if (body.isJumbomoji) {
      displayUnderneathBody()
      return true
    }

    val availableTuck = bodyContainer.measuredWidth - lastLineWidth - (horizontalFooterPadding * 2)
    if (body.lineCount > 1 && availableTuck > footerWidth) {
      return false
    }

    val availableWidth = maxWidth - lastLineWidth
    if (body.lineCount == 1 && availableWidth > footerWidth) {
      displayAtEndOfBody()
      return true
    }

    displayUnderneathBody()
    return true
  }

  private fun displayUnderneathBody() {
    if (displayState == DisplayState.UNDERNEATH) {
      return
    }

    footerViews.forEach {
      it.translationY = 0f
    }

    bodyContainer.padding(right = 0, left = 0, bottom = footerViews.first().measuredHeight)
    displayState = DisplayState.UNDERNEATH
  }

  private fun displayAtEndOfBody() {
    if (displayState == DisplayState.END) {
      return
    }

    footerViews.forEach {
      it.translationY = 0f
    }

    val end = footerViews.sumOf { it.measuredWidth } + if (isIncoming) 4.dp else 8.dp
    val (left, right) = if (bodyContainer.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
      0 to end
    } else {
      end to 0
    }

    bodyContainer.padding(right = right, left = left, bottom = 0)
    displayState = DisplayState.END
  }

  private fun displayTuckedIntoBody() {
    if (displayState == DisplayState.TUCKED) {
      return
    }

    footerViews.forEach {
      it.translationY = 0f
    }

    bodyContainer.padding(right = 0, left = 0, bottom = 0)
    displayState = DisplayState.TUCKED
  }

  private enum class DisplayState {
    NONE,
    UNDERNEATH,
    END,
    TUCKED
  }
}

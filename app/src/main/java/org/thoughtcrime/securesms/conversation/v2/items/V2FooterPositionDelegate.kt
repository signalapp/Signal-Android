/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.view.View
import android.widget.Space
import android.widget.TextView
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.view.Stub
import org.signal.core.util.BidiUtil
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.DeliveryStatusView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible
import kotlin.math.max

/**
 * Logical delegate for determining the footer position for a particular conversation item.
 */
class V2FooterPositionDelegate private constructor(
  private val root: V2ConversationItemLayout,
  private val footerViews: List<View>,
  private val bodyContainer: View,
  private val body: EmojiTextView,
  private val footerSpacer: Space?,
  private val thumbnailView: Stub<V2ConversationItemThumbnail>?
) : V2ConversationItemLayout.OnMeasureListener {

  constructor(binding: V2ConversationItemTextOnlyBindingBridge) : this(
    binding.root,
    listOfNotNull(
      binding.footerDate,
      binding.deliveryStatus,
      binding.footerExpiry,
      binding.footerSpace,
      binding.footerPinned,
      binding.footerStarred
    ),
    binding.bodyWrapper,
    binding.body,
    binding.footerSpace,
    null
  )

  constructor(binding: V2ConversationItemMediaBindingBridge) : this(
    binding.textBridge.root,
    listOfNotNull(
      binding.textBridge.footerDate,
      binding.textBridge.deliveryStatus,
      binding.textBridge.footerExpiry,
      binding.textBridge.footerSpace,
      binding.textBridge.footerPinned
    ),
    binding.textBridge.bodyWrapper,
    binding.textBridge.body,
    binding.textBridge.footerSpace,
    binding.thumbnailStub
  )

  private val gutters = 48.dp + 16.dp
  private val horizontalFooterPadding = root.context.resources.getDimensionPixelOffset(R.dimen.message_bubble_horizontal_padding)

  private var displayState: DisplayState = DisplayState.NONE

  private var decidedWidthMeasureSpec: Int = 0
  private var decidedBodyText: CharSequence? = null
  private var decidedFooterSignature: Int = 0
  private var skipDecision: Boolean = false

  override fun onPreMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    skipDecision = displayState != DisplayState.NONE &&
      widthMeasureSpec == decidedWidthMeasureSpec &&
      body.text === decidedBodyText &&
      footerSignature() == decidedFooterSignature

    if (skipDecision) {
      return
    }

    displayTuckedIntoBody()
  }

  override fun onPostMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Boolean {
    if (skipDecision) {
      return false
    }

    decidedWidthMeasureSpec = widthMeasureSpec
    decidedBodyText = body.text
    decidedFooterSignature = footerSignature()

    val maxWidth = if (thumbnailView?.isVisible == true) {
      thumbnailView.get().layoutParams.width
    } else {
      root.measuredWidth - gutters
    }

    val lastLineWidth = body.lastLineWidth
    val footerWidth = getFooterWidth()

    if (footerViews.all { !it.visible }) {
      return false
    }

    if (body.isJumbomoji || BidiUtil.hasMixedTextDirection(body.text)) {
      displayUnderneathBody()
      return true
    }

    val availableTuck = bodyContainer.measuredWidth - lastLineWidth - (horizontalFooterPadding * 2)
    if (body.lineCount > 1 && availableTuck > footerWidth) {
      return false
    }

    val availableWidth = maxWidth - lastLineWidth - (horizontalFooterPadding * 2)
    if (body.lineCount == 1 && availableWidth > (footerWidth + 8.dp)) {
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

    val horizontalPadding = max(0, getFooterWidth() - (footerSpacer?.measuredWidth ?: 0) - body.measuredWidth)
    val (left, right) = if (bodyContainer.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
      0 to horizontalPadding
    } else {
      horizontalPadding to 0
    }

    body.padding(right = right, left = left, bottom = footerViews.first().measuredHeight)
    displayState = DisplayState.UNDERNEATH
  }

  private fun displayAtEndOfBody() {
    if (displayState == DisplayState.END) {
      return
    }

    val (left, right) = if (bodyContainer.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
      0 to (getFooterWidth() - 8.dp)
    } else {
      (getFooterWidth() - 8.dp) to 0
    }

    body.padding(right = right, left = left, bottom = 0)
    displayState = DisplayState.END
  }

  private fun displayTuckedIntoBody() {
    if (displayState == DisplayState.TUCKED) {
      return
    }

    body.padding(right = 0, left = 0, bottom = 0)
    displayState = DisplayState.TUCKED
  }

  /**
   * Everything other than the body text and the width that can move the footer. The same signature on a later
   * measure pass means the earlier decision still holds, so the layout is measured once rather than twice.
   */
  private fun footerSignature(): Int {
    var signature = if (thumbnailView?.isVisible == true) thumbnailView.get().layoutParams.width else -1
    for (view in footerViews) {
      signature = signature * 31 + (if (view.visible) 1 else 0)
      if (view is TextView) {
        signature = signature * 31 + view.text.hashCode()
      }
    }
    return signature
  }

  private fun getFooterWidth(): Int {
    return footerViews.sumOf { it.footerWidth() + ViewUtil.getLeftMargin(it) + ViewUtil.getRightMargin(it) }
  }

  private fun View.footerWidth(): Int {
    return if (this is DeliveryStatusView) reservedWidth else measuredWidth
  }

  private enum class DisplayState {
    NONE,
    UNDERNEATH,
    END,
    TUCKED
  }
}

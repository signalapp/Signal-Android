/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.Canvas
import org.thoughtcrime.securesms.util.visible

/**
 * Responsible for drawing the conversation bubble when a user long-presses it and the reaction
 * overlay appears.
 */
class V2ConversationItemSnapshotStrategy(
  private val binding: V2ConversationItemTextOnlyBindingBridge
) : InteractiveConversationElement.SnapshotStrategy {

  private val viewsToRestoreScale = listOfNotNull(
    binding.bodyWrapper,
    binding.footerBackground,
    binding.footerDate,
    binding.footerExpiry,
    binding.deliveryStatus,
    binding.reactions
  )

  private val viewsToHide = listOfNotNull(
    binding.senderPhoto,
    binding.senderBadge
  )

  override val snapshotMetrics = InteractiveConversationElement.SnapshotMetrics(
    snapshotOffset = 0f,
    contextMenuPadding = binding.bodyWrapper.x
  )

  override fun snapshot(canvas: Canvas) {
    val originalScales = viewsToRestoreScale.associateWith { Pair(it.scaleX, it.scaleY) }
    viewsToRestoreScale.forEach {
      it.scaleX = 1f
      it.scaleY = 1f
    }

    val originalVisibility = viewsToHide.associateWith { it.visibility }
    viewsToHide.forEach { it.visible = false }

    binding.root.draw(canvas)

    originalVisibility.forEach { (view, visibility) -> view.visibility = visibility }
    originalScales.forEach { view, (scaleX, scaleY) ->
      view.scaleX = scaleX
      view.scaleY = scaleY
    }
  }
}

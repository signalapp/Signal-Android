/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.util.hasNoBubble

/**
 * Color information for conversation items.
 */
class V2ConversationItemTheme(
  private val context: Context,
  private val conversationContext: V2ConversationContext
) {

  @ColorInt
  fun getReplyIconBackgroundColor(): Int {
    return if (conversationContext.hasWallpaper()) {
      ContextCompat.getColor(context, R.color.signal_colorSurface1)
    } else {
      Color.TRANSPARENT
    }
  }

  @ColorInt
  fun getFooterIconColor(
    conversationMessage: ConversationMessage
  ): Int {
    return getColor(
      conversationMessage,
      conversationContext.getColorizer()::getOutgoingFooterIconColor,
      conversationContext.getColorizer()::getIncomingFooterIconColor
    )
  }

  @ColorInt
  fun getFooterTextColor(
    conversationMessage: ConversationMessage
  ): Int {
    return getColor(
      conversationMessage,
      conversationContext.getColorizer()::getOutgoingFooterTextColor,
      conversationContext.getColorizer()::getIncomingFooterTextColor
    )
  }

  @ColorInt
  fun getBodyTextColor(
    conversationMessage: ConversationMessage
  ): Int {
    return getColor(
      conversationMessage,
      conversationContext.getColorizer()::getOutgoingBodyTextColor,
      conversationContext.getColorizer()::getIncomingBodyTextColor
    )
  }

  fun getBodyBubbleColor(
    conversationMessage: ConversationMessage
  ): ColorStateList {
    if (conversationMessage.messageRecord.hasNoBubble(context)) {
      return ColorStateList.valueOf(Color.TRANSPARENT)
    }

    return getFooterBubbleColor(conversationMessage)
  }

  fun getFooterBubbleColor(
    conversationMessage: ConversationMessage
  ): ColorStateList {
    return ColorStateList.valueOf(
      if (conversationMessage.messageRecord.isOutgoing) {
        Color.TRANSPARENT
      } else {
        if (conversationContext.hasWallpaper()) {
          ContextCompat.getColor(context, R.color.signal_colorSurface)
        } else {
          ContextCompat.getColor(context, R.color.signal_colorSurface2)
        }
      }
    )
  }

  @ColorInt
  private fun getColor(
    conversationMessage: ConversationMessage,
    outgoingColor: (Context) -> Int,
    incomingColor: (Context, Boolean) -> Int
  ): Int {
    return if (conversationMessage.messageRecord.isOutgoing) {
      outgoingColor(context)
    } else {
      incomingColor(context, conversationContext.hasWallpaper())
    }
  }
}

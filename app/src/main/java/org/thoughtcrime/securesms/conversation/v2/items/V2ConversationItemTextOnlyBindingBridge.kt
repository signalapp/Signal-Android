/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.view.View
import android.view.ViewGroup
import android.widget.Space
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AlertView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.DeliveryStatusView
import org.thoughtcrime.securesms.components.ExpirationTimerView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyIncomingBinding
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyOutgoingBinding
import org.thoughtcrime.securesms.reactions.ReactionsConversationView

/**
 * Pass-through interface for bridging incoming and outgoing text-only message views.
 *
 * Essentially, just a convenience wrapper since the layouts differ *very slightly* and
 * we want to be able to have each follow the same code-path.
 */
data class V2ConversationItemTextOnlyBindingBridge(
  val root: V2ConversationItemLayout,
  val senderName: EmojiTextView?,
  val senderPhoto: AvatarImageView?,
  val senderBadge: BadgeImageView?,
  val conversationItemBodyWrapper: ViewGroup,
  val conversationItemBody: EmojiTextView,
  val conversationItemReply: ShapeableImageView,
  val conversationItemReactions: ReactionsConversationView,
  val conversationItemDeliveryStatus: DeliveryStatusView?,
  val conversationItemFooterDate: TextView,
  val conversationItemFooterExpiry: ExpirationTimerView,
  val conversationItemFooterBackground: View,
  val conversationItemFooterSpace: Space?,
  val conversationItemAlert: AlertView?,
  val isIncoming: Boolean
)

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemTextOnlyIncomingBinding.bridge(): V2ConversationItemTextOnlyBindingBridge {
  return V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderName = groupMessageSender,
    senderPhoto = contactPhoto,
    senderBadge = badge,
    conversationItemBody = conversationItemBody,
    conversationItemBodyWrapper = conversationItemBodyWrapper,
    conversationItemReply = conversationItemReply,
    conversationItemReactions = conversationItemReactions,
    conversationItemDeliveryStatus = null,
    conversationItemFooterDate = conversationItemFooterDate,
    conversationItemFooterExpiry = conversationItemExpirationTimer,
    conversationItemFooterBackground = conversationItemFooterBackground,
    conversationItemAlert = null,
    conversationItemFooterSpace = null,
    isIncoming = false
  )
}

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemTextOnlyOutgoingBinding.bridge(): V2ConversationItemTextOnlyBindingBridge {
  return V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderName = null,
    senderPhoto = null,
    senderBadge = null,
    conversationItemBody = conversationItemBody,
    conversationItemBodyWrapper = conversationItemBodyWrapper,
    conversationItemReply = conversationItemReply,
    conversationItemReactions = conversationItemReactions,
    conversationItemDeliveryStatus = conversationItemDeliveryStatus,
    conversationItemFooterDate = conversationItemFooterDate,
    conversationItemFooterExpiry = conversationItemExpirationTimer,
    conversationItemFooterBackground = conversationItemFooterBackground,
    conversationItemAlert = conversationItemAlert,
    conversationItemFooterSpace = footerEndPad,
    isIncoming = false
  )
}

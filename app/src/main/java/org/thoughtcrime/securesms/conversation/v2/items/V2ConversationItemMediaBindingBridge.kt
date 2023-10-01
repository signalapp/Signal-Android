/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.widget.Space
import org.thoughtcrime.securesms.components.QuoteView
import org.thoughtcrime.securesms.databinding.V2ConversationItemMediaIncomingBinding
import org.thoughtcrime.securesms.databinding.V2ConversationItemMediaOutgoingBinding
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Pass-through interface for bridging incoming and outgoing media message views.
 *
 * Essentially, just a convenience wrapper since the layouts differ *very slightly* and
 * we want to be able to have each follow the same code-path.
 */
data class V2ConversationItemMediaBindingBridge(
  val textBridge: V2ConversationItemTextOnlyBindingBridge,
  val thumbnailStub: Stub<V2ConversationItemThumbnail>,
  val quoteStub: Stub<QuoteView>,
  val bodyContentSpacer: Space
)

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemMediaIncomingBinding.bridge(): V2ConversationItemMediaBindingBridge {
  val textBridge = V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderName = groupMessageSender,
    senderPhoto = contactPhoto,
    senderBadge = badge,
    body = conversationItemBody,
    bodyWrapper = conversationItemBodyWrapper,
    reply = conversationItemReply,
    reactions = conversationItemReactions,
    deliveryStatus = null,
    footerDate = conversationItemFooterDate,
    footerExpiry = conversationItemExpirationTimer,
    footerBackground = conversationItemFooterBackground,
    alert = null,
    footerSpace = null,
    isIncoming = true
  )

  return V2ConversationItemMediaBindingBridge(
    textBridge = textBridge,
    thumbnailStub = Stub(conversationItemThumbnailStub),
    quoteStub = Stub(conversationItemQuoteStub),
    bodyContentSpacer = conversationItemContentSpacer
  )
}

/**
 * Wraps the binding in the bridge.
 */
fun V2ConversationItemMediaOutgoingBinding.bridge(): V2ConversationItemMediaBindingBridge {
  val textBridge = V2ConversationItemTextOnlyBindingBridge(
    root = root,
    senderName = null,
    senderPhoto = null,
    senderBadge = null,
    body = conversationItemBody,
    bodyWrapper = conversationItemBodyWrapper,
    reply = conversationItemReply,
    reactions = conversationItemReactions,
    deliveryStatus = conversationItemDeliveryStatus,
    footerDate = conversationItemFooterDate,
    footerExpiry = conversationItemExpirationTimer,
    footerBackground = conversationItemFooterBackground,
    alert = conversationItemAlert,
    footerSpace = footerEndPad,
    isIncoming = false
  )

  return V2ConversationItemMediaBindingBridge(
    textBridge = textBridge,
    thumbnailStub = Stub(conversationItemThumbnailStub),
    quoteStub = Stub(conversationItemQuoteStub),
    bodyContentSpacer = conversationItemContentSpacer
  )
}

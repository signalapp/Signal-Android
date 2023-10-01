/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart

/**
 * Temporary shared interface between the two conversation adapters strictly for use in
 * shared decorators and other utils.
 */
interface ConversationAdapterBridge {
  companion object {
    const val PAYLOAD_TIMESTAMP = 0
    const val PAYLOAD_NAME_COLORS = 1
    const val PAYLOAD_SELECTED = 2
    const val PAYLOAD_PARENT_SCROLLING = 3
  }

  fun hasNoConversationMessages(): Boolean
  fun getConversationMessage(position: Int): ConversationMessage?
  fun consumePulseRequest(): PulseRequest?

  val selectedItems: Set<MultiselectPart>

  data class PulseRequest(val position: Int, val isOutgoing: Boolean)
}

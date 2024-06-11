/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * Describes the Adapter "context" that would normally have been
 * visible to an inner class.
 */
interface V2ConversationContext {
  val lifecycleOwner: LifecycleOwner
  val requestManager: RequestManager
  val displayMode: ConversationItemDisplayMode
  val clickListener: ConversationAdapter.ItemClickListener
  val selectedItems: Set<MultiselectPart>
  val isMessageRequestAccepted: Boolean
  val searchQuery: String?
  val isParentInScroll: Boolean

  fun getChatColorsData(): ChatColorsDrawable.ChatColorsData

  fun onStartExpirationTimeout(messageRecord: MessageRecord)

  fun hasWallpaper(): Boolean
  fun getColorizer(): Colorizer
  fun getNextMessage(adapterPosition: Int): MessageRecord?
  fun getPreviousMessage(adapterPosition: Int): MessageRecord?
}

package org.thoughtcrime.securesms.conversationlist.chatfilter

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter

@Parcelize
data class ConversationFilterRequest(
  val filter: ConversationFilter,
  val source: ConversationFilterSource
) : Parcelable

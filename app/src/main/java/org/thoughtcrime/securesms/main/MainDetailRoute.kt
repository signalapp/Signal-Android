/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.signal.core.ui.compose.split.DetailNavKey
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

/**
 * Describes which content to display in the detail pane.
 */
@Serializable
@Parcelize
sealed interface MainDetailRoute : Parcelable, DetailNavKey {

  /**
   * Whether this is the bottom-most piece of detail content rather than something stacked on top of it.
   * Pushing one replaces the detail already displayed above the current list; see [push].
   */
  @IgnoredOnParcel
  override val isContentRoot: Boolean
    get() = false

  @Serializable
  data class Conversation(val conversationArgs: ConversationArgs) : MainDetailRoute {
    @Transient
    @IgnoredOnParcel
    override val isContentRoot: Boolean = true

    @Transient
    @IgnoredOnParcel
    val controllerKey: Long = conversationArgs.threadId
  }

  @Serializable
  data class CallLinkDetails(val callLinkRoomId: CallLinkRoomId) : MainDetailRoute {
    @Transient
    @IgnoredOnParcel
    override val isContentRoot: Boolean = true

    @Transient
    @IgnoredOnParcel
    val controllerKey: CallLogRow.Id = CallLogRow.Id.CallLink(callLinkRoomId)
  }

  /**
   * Subscreens that can be displayed within the chats tab.
   */
  @Parcelize
  sealed interface Chats : MainDetailRoute {

    val controllerKey: RecipientId

    @Serializable
    data class MessageDetails(val recipientId: RecipientId, val messageId: MessageId) : Chats {
      @Transient
      @IgnoredOnParcel
      override val controllerKey: RecipientId = recipientId
    }

    @Serializable
    data class ConversationSettings(
      val recipientId: RecipientId
    ) : Chats {
      @Transient
      @IgnoredOnParcel
      override val isContentRoot: Boolean = false

      @Transient
      @IgnoredOnParcel
      override val controllerKey: RecipientId = recipientId
    }
  }

  /**
   * Subscreens that can be displayed within the calls tab.
   */
  @Parcelize
  sealed interface Calls : MainDetailRoute {
    val controllerKey: CallLogRow.Id

    @Parcelize
    sealed class CallLinks : Calls {
      @Serializable
      data class EditCallLinkName(
        val callLinkRoomId: CallLinkRoomId,
        val currentName: String = ""
      ) : CallLinks() {
        @Transient
        @IgnoredOnParcel
        override val controllerKey: CallLogRow.Id = CallLogRow.Id.CallLink(callLinkRoomId)
      }
    }
  }

  /**
   * Subscreens that can be displayed within the stories tab.
   */
  @Parcelize
  sealed class Stories : MainDetailRoute {
    @Transient
    @IgnoredOnParcel
    override val isContentRoot: Boolean = true

    @Serializable data object Archive : Stories()

    @Serializable data object MyStories : Stories()

    @Serializable data object PrivacySettings : Stories()
  }
}

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
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

/**
 * Describes which content to display in the detail view.
 */
@Parcelize
sealed interface MainNavigationDetailLocation : Parcelable {
  @Serializable
  data object Empty : MainNavigationDetailLocation

  @Parcelize
  sealed interface Chats : MainNavigationDetailLocation {

    val controllerKey: RecipientId

    @Serializable
    data class Conversation(val conversationArgs: ConversationArgs) : Chats {
      @Transient
      @IgnoredOnParcel
      override val controllerKey: RecipientId = conversationArgs.recipientId
    }
  }

  /**
   * Content which can be displayed while the user is navigating the Calls tab.
   */
  @Parcelize
  sealed interface Calls : MainNavigationDetailLocation {

    val controllerKey: CallLinkRoomId

    @Serializable
    data class CallLinkDetails(val callLinkRoomId: CallLinkRoomId) : Calls {
      @Transient
      @IgnoredOnParcel
      override val controllerKey: CallLinkRoomId = callLinkRoomId
    }

    @Serializable
    data class EditCallLinkName(val callLinkRoomId: CallLinkRoomId) : Calls {
      @Transient
      @IgnoredOnParcel
      override val controllerKey: CallLinkRoomId = callLinkRoomId
    }
  }

  @Parcelize
  sealed interface Stories : MainNavigationDetailLocation
}

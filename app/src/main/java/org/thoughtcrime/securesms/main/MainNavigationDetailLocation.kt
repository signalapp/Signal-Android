/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.os.Parcelable
import androidx.compose.runtime.saveable.SaverScope
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

/**
 * Describes which content to display in the detail view.
 */
@Serializable
@Parcelize
sealed class MainNavigationDetailLocation : Parcelable {

  class Saver : androidx.compose.runtime.saveable.Saver<MainNavigationDetailLocation, String> {
    override fun SaverScope.save(value: MainNavigationDetailLocation): String? {
      return Json.encodeToString(value)
    }

    override fun restore(value: String): MainNavigationDetailLocation? {
      return Json.decodeFromString(value)
    }
  }

  /**
   * Flag utilized internally to determine whether the given route is displayed at the root
   * of a task stack (or on top of Empty)
   */
  @IgnoredOnParcel
  open val isContentRoot: Boolean = false

  @Serializable
  data object Empty : MainNavigationDetailLocation() {
    @Transient
    @IgnoredOnParcel
    override val isContentRoot: Boolean = true
  }

  @Parcelize
  sealed class Chats : MainNavigationDetailLocation() {

    abstract val controllerKey: RecipientId

    @Serializable
    data class Conversation(val conversationArgs: ConversationArgs) : Chats() {
      @Transient
      @IgnoredOnParcel
      override val isContentRoot: Boolean = true

      @Transient
      @IgnoredOnParcel
      override val controllerKey: RecipientId = conversationArgs.recipientId
    }
  }

  /**
   * Content which can be displayed while the user is navigating the Calls tab.
   */
  @Parcelize
  sealed class Calls : MainNavigationDetailLocation() {

    @Parcelize
    sealed class CallLinks : Calls() {

      abstract val controllerKey: CallLinkRoomId

      @Serializable
      data class CallLinkDetails(val callLinkRoomId: CallLinkRoomId) : CallLinks() {
        @Transient
        @IgnoredOnParcel
        override val isContentRoot: Boolean = true

        @Transient
        @IgnoredOnParcel
        override val controllerKey: CallLinkRoomId = callLinkRoomId
      }

      @Serializable
      data class EditCallLinkName(val callLinkRoomId: CallLinkRoomId) : CallLinks() {
        @Transient
        @IgnoredOnParcel
        override val controllerKey: CallLinkRoomId = callLinkRoomId
      }
    }
  }

  @Parcelize
  sealed class Stories : MainNavigationDetailLocation()
}

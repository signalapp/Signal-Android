/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import org.thoughtcrime.securesms.main.MainDetailBackStack
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Controls the navigation stack used by the chats screen.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class ChatsBackStack(savedStateHandle: SavedStateHandle) : MainDetailBackStack {

  companion object {
    private const val KEY = "chats_back_stack"

    val saver: Saver<SnapshotStateList<MainNavigationDetailLocation>, ArrayList<MainNavigationDetailLocation>> = Saver(
      save = { ArrayList(it) },
      restore = { mutableStateListOf(*it.toTypedArray()) }
    )
  }

  override val entries: SnapshotStateList<MainNavigationDetailLocation> = savedStateHandle.saveable(
    key = KEY,
    saver = saver
  ) {
    mutableStateListOf(MainNavigationDetailLocation.Empty)
  }

  val activeRecipientId: RecipientId?
    get() = entries.asReversed().firstNotNullOfOrNull {
      when (it) {
        is MainNavigationDetailLocation.Conversation -> it.conversationArgs.recipientId
        is MainNavigationDetailLocation.Chats -> it.controllerKey
        else -> null
      }
    }

  /**
   * Pushes an entry onto the stack.
   */
  override fun push(location: MainNavigationDetailLocation) {
    when (location) {
      is MainNavigationDetailLocation.Empty, entries.lastOrNull() -> Unit

      is MainNavigationDetailLocation.Conversation -> {
        entries.removeAll { it !is MainNavigationDetailLocation.Empty }
        entries.add(location)
      }

      else -> entries.add(location)
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.backStack
import org.signal.core.ui.compose.split.ListDetailBackStack
import org.signal.core.ui.compose.split.push
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

/**
 * That a stack survives process death at all is covered by `ListDetailBackStackPersistenceTest` in
 * `core:ui`. What is left here is that *these* routes survive it: every key is serialized by its concrete
 * class, so each route class has to carry a serializer that round-trips its own arguments.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainDetailBackStackPersistenceTest {

  private class Host(savedStateHandle: SavedStateHandle) {
    val backStack: ListDetailBackStack by savedStateHandle.backStack("test_back_stack", MainListRoute.Chats)
  }

  /** Saves and restores the handle the way a process death would. */
  private fun SavedStateHandle.roundTrip(): SavedStateHandle {
    return SavedStateHandle.createHandle(savedStateProvider().saveState(), null)
  }

  @Test
  fun `given a stacked detail location, when restored, then the whole stack comes back`() {
    val handle = SavedStateHandle()
    val roomId = CallLinkRoomId.fromBytes(byteArrayOf(7, 8, 9))
    Host(handle).backStack.apply {
      push(MainDetailRoute.CallLinkDetails(roomId))
      push(MainDetailRoute.Calls.CallLinks.EditCallLinkName(roomId, "movie night"))
    }

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(
      listOf(
        MainListRoute.Chats,
        MainDetailRoute.CallLinkDetails(roomId),
        MainDetailRoute.Calls.CallLinks.EditCallLinkName(roomId, "movie night")
      ),
      restored.toList()
    )
  }

  @Test
  fun `given a chats sub screen, when restored, then its arguments come back`() {
    val handle = SavedStateHandle()
    val recipientId = RecipientId.from(12)
    Host(handle).backStack.push(MainDetailRoute.Chats.ConversationSettings(recipientId))

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(
      MainDetailRoute.Chats.ConversationSettings(recipientId),
      restored.last()
    )
  }

  /**
   * [MainListRoute] is an enum, which `@Parcelize` cannot handle — this is what lets the
   * list locations sit at the root of a stack once they move into it.
   */
  @Test
  fun `given a list location on the stack, when restored, then the enum entry comes back`() {
    val handle = SavedStateHandle()
    Host(handle).backStack.push(MainListRoute.Archive)

    val restored = Host(handle.roundTrip()).backStack

    assertEquals(MainListRoute.Archive, restored.last())
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.mockk
import org.junit.Test

class ConversationDataTest {

  private fun build(
    firstUnreadId: Long = -1,
    firstUnreadPosition: Int = 0,
    lastScrolledPosition: Int = 0,
    jumpToPosition: Int = -1,
    threadSize: Int = 100,
    isMessageRequestAccepted: Boolean = true
  ): ConversationData {
    return ConversationData(
      threadRecipient = mockk(),
      threadId = 1L,
      firstUnreadId = firstUnreadId,
      firstUnreadPosition = firstUnreadPosition,
      lastScrolledPosition = lastScrolledPosition,
      jumpToPosition = jumpToPosition,
      threadSize = threadSize,
      messageRequestData = ConversationData.MessageRequestData(isMessageRequestAccepted = isMessageRequestAccepted, isHidden = false),
      showUniversalExpireTimerMessage = false,
      unreadCount = if (firstUnreadPosition > 0) 1 else 0,
      groupMemberAcis = emptyList()
    )
  }

  @Test
  fun `getStartPosition prefers a jump target over everything else`() {
    val data = build(jumpToPosition = 42, firstUnreadPosition = 10, lastScrolledPosition = 5)
    assertThat(data.getStartPosition()).isEqualTo(42)
  }

  @Test
  fun `getStartPosition uses firstUnreadPosition when accepted and there is unread`() {
    val data = build(firstUnreadPosition = 7, lastScrolledPosition = 5)
    assertThat(data.getStartPosition()).isEqualTo(7)
  }

  @Test
  fun `getStartPosition falls back to lastScrolledPosition when accepted with no unread`() {
    val data = build(firstUnreadPosition = 0, lastScrolledPosition = 5)
    assertThat(data.getStartPosition()).isEqualTo(5)
  }

  @Test
  fun `getStartPosition uses threadSize (bottom) when the message request is not accepted`() {
    val data = build(firstUnreadPosition = 7, lastScrolledPosition = 5, threadSize = 99, isMessageRequestAccepted = false)
    assertThat(data.getStartPosition()).isEqualTo(99)
  }

  @Test
  fun `shouldScrollToFirstUnread is true only for a positive position`() {
    assertThat(build(firstUnreadPosition = 1).shouldScrollToFirstUnread()).isTrue()
    assertThat(build(firstUnreadPosition = 0).shouldScrollToFirstUnread()).isFalse()
  }

  @Test
  fun `shouldJumpToMessage is true for a non-negative position`() {
    assertThat(build(jumpToPosition = 0).shouldJumpToMessage()).isTrue()
    assertThat(build(jumpToPosition = -1).shouldJumpToMessage()).isFalse()
  }
}

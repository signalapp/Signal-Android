/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectCollection
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig

class MenuStateAccessibilityContractTest {

  @Before
  fun setUp() {
    RemoteConfig.REMOTE_VALUES.clear()
    RemoteConfig.initialized = true
  }

  @After
  fun tearDown() {
    RemoteConfig.REMOTE_VALUES.clear()
    RemoteConfig.initialized = false
  }

  @Test
  fun singleMessage_exposesCoreActions() {
    val scenario = buildSingleMessageScenario()
    val menuState = getMenuState(scenario)

    assertTrue(menuState.shouldShowForwardAction())
    assertTrue(menuState.shouldShowReplyAction())
    assertTrue(menuState.shouldShowDetailsAction())
    assertFalse(menuState.shouldShowCopyAction())
    assertTrue(menuState.shouldShowDeleteAction())
    assertTrue(menuState.shouldShowReactions())
    assertFalse(menuState.shouldShowPinMessage())
    assertFalse(menuState.showShowUnpinMessage())
  }

  @Test
  fun messageRequest_hidesReply() {
    val scenario = buildSingleMessageScenario(shouldShowMessageRequest = true)
    val menuState = getMenuState(scenario)

    assertFalse(menuState.shouldShowReplyAction())
    assertTrue(menuState.shouldShowForwardAction())
    assertTrue(menuState.shouldShowDeleteAction())
  }

  @Test
  fun blockedSender_hidesReply() {
    val scenario = buildSingleMessageScenario(isSenderBlocked = true)
    val menuState = getMenuState(scenario)

    assertFalse(menuState.shouldShowReplyAction())
    assertTrue(menuState.shouldShowForwardAction())
    assertTrue(menuState.shouldShowDeleteAction())
  }

  private fun getMenuState(scenario: Scenario): MenuState {
    return MenuState.getMenuState(
      scenario.recipient,
      scenario.selectedParts,
      scenario.shouldShowMessageRequest,
      scenario.isNonAdminInAnnouncementGroup,
      scenario.canEditGroupInfo
    )
  }

  private fun buildSingleMessageScenario(
    shouldShowMessageRequest: Boolean = false,
    isNonAdminInAnnouncementGroup: Boolean = false,
    canEditGroupInfo: Boolean = false,
    isSenderBlocked: Boolean = false
  ): Scenario {
    val sender = mockk<Recipient>(relaxed = true).apply {
      every { isBlocked } returns isSenderBlocked
    }

    val recipient = mockk<Recipient>(relaxed = true).apply {
      every { isReleaseNotes } returns false
      every { isGroup } returns false
      every { isActiveGroup } returns true
    }

    val messageRecord = mockk<MessageRecord>(relaxed = true).apply {
      every { body } returns ""
      every { isInMemoryMessageRecord } returns false
      every { isUpdate } returns false
      every { isMms } returns false
      every { isViewOnce } returns false
      every { isRemoteDelete } returns false
      every { isFailed } returns false
      every { isPending } returns false
      every { isSecure } returns true
      every { isPaymentNotification } returns false
      every { isPaymentTombstone } returns false
      every { fromRecipient } returns sender
    }

    val conversationMessage = mockk<ConversationMessage>(relaxed = true)
    every { conversationMessage.messageRecord } returns messageRecord

    val part = MultiselectPart.Text(conversationMessage)
    every { conversationMessage.multiselectCollection } returns MultiselectCollection.Single(part)

    return Scenario(
      recipient = recipient,
      conversationMessage = conversationMessage,
      selectedParts = setOf(part),
      shouldShowMessageRequest = shouldShowMessageRequest,
      isNonAdminInAnnouncementGroup = isNonAdminInAnnouncementGroup,
      canEditGroupInfo = canEditGroupInfo
    )
  }

  private data class Scenario(
    val recipient: Recipient,
    val conversationMessage: ConversationMessage,
    val selectedParts: Set<MultiselectPart>,
    val shouldShowMessageRequest: Boolean,
    val isNonAdminInAnnouncementGroup: Boolean,
    val canEditGroupInfo: Boolean
  )
}

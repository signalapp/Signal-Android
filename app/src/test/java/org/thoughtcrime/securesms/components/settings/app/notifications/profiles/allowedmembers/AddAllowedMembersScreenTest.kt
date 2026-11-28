/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.emoji.EmojiDependenciesRule
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddAllowedMembersScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  /**
   * Member rows draw their avatars with AvatarImageView, which reads the recipient cache out of AppDependencies and chat
   * colors out of SignalStore, and render names with Emojifier.
   */
  @get:Rule
  val dependencies: RuleChain = RuleChain
    .outerRule(MockAppDependenciesRule())
    .around(SignalStoreRule())
    .around(EmojiDependenciesRule(ApplicationProvider.getApplicationContext()))

  private val events = mutableListOf<AddAllowedMembersEvent>()

  @Test
  fun `nothing but the toolbar is shown until the profile loads`() {
    setContent(AddAllowedMembersState())

    composeTestRule.onNodeWithTag(AddAllowedMembersTestTags.LIST).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AddAllowedMembersTestTags.BUTTON_NEXT).assertDoesNotExist()
  }

  @Test
  fun `clicking add people emits AddMembersClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithTag(AddAllowedMembersTestTags.ROW_ADD_MEMBERS).performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.AddMembersClicked)
  }

  @Test
  fun `clicking a member's remove button emits RemoveMemberClicked`() {
    setContent(loadedState(members = listOf(recipient(1, "Alice"))))

    scrollTo(AddAllowedMembersTestTags.member(RecipientId.from(1)))
    composeTestRule.onNode(hasContentDescription("Delete") and hasAnyAncestor(hasTestTag(AddAllowedMembersTestTags.member(RecipientId.from(1))))).performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.RemoveMemberClicked(RecipientId.from(1)))
  }

  @Test
  fun `flipping allow all calls emits AllowAllCallsToggled with the new value`() {
    setContent(loadedState(allowAllCalls = true))

    scrollTo(AddAllowedMembersTestTags.ROW_ALLOW_ALL_CALLS)
    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(AddAllowedMembersTestTags.ROW_ALLOW_ALL_CALLS))).performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.AllowAllCallsToggled(false))
  }

  @Test
  fun `flipping notify for all mentions emits NotifyForAllMentionsToggled with the new value`() {
    setContent(loadedState(allowAllMentions = false))

    scrollTo(AddAllowedMembersTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS)
    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(AddAllowedMembersTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS))).performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.NotifyForAllMentionsToggled(true))
  }

  @Test
  fun `clicking next emits NextClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithTag(AddAllowedMembersTestTags.BUTTON_NEXT).performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.NextClicked)
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(AddAllowedMembersEvent.NavigateBackClicked)
  }

  private fun setContent(state: AddAllowedMembersState) {
    composeTestRule.setContent {
      AddAllowedMembersScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AddAllowedMembersTestTags.LIST)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun loadedState(
    members: List<Recipient> = emptyList(),
    allowAllCalls: Boolean = true,
    allowAllMentions: Boolean = false
  ): AddAllowedMembersState {
    return AddAllowedMembersState(
      profile = NotificationProfile(
        id = 1L,
        name = "Work",
        emoji = "",
        createdAt = 0L,
        schedule = NotificationProfileSchedule(id = 1L),
        allowAllCalls = allowAllCalls,
        allowAllMentions = allowAllMentions,
        allowedMembers = members.map { it.id }.toSet(),
        notificationProfileId = NotificationProfileId(UUID(0L, 1L))
      ),
      members = members
    )
  }

  private fun recipient(id: Long, name: String): Recipient {
    return Recipient(
      id = RecipientId.from(id),
      isResolving = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      systemContactName = name
    )
  }
}

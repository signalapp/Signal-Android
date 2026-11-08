/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.signal.core.ui.compose.Dialogs
import org.signal.emoji.EmojiDependenciesRule
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import java.time.DayOfWeek
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationProfileDetailsScreenTest {

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

  private val events = mutableListOf<NotificationProfileDetailsEvent>()

  @Test
  fun `nothing but the toolbar is shown until the profile loads`() {
    setContent(NotificationProfileDetailsState())

    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.LIST).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.BUTTON_EDIT).assertDoesNotExist()
  }

  @Test
  fun `clicking edit emits EditClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.BUTTON_EDIT).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.EditClicked)
  }

  @Test
  fun `flipping the profile switch emits ProfileToggled`() {
    setContent(loadedState())

    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(NotificationProfileDetailsTestTags.ROW_PROFILE))).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.ProfileToggled)
  }

  @Test
  fun `the profile shows how long it stays on`() {
    setContent(loadedState().copy(activeStatus = NotificationProfiles.ActiveStatus.On))

    composeTestRule.onNodeWithText("On").assertExists()
  }

  @Test
  fun `clicking add people emits AddMembersClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.ROW_ADD_MEMBERS).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.AddMembersClicked)
  }

  @Test
  fun `clicking a member's remove button emits RemoveMemberClicked`() {
    setContent(loadedState(members = listOf(recipient(1, "Alice"))))

    scrollTo(NotificationProfileDetailsTestTags.member(RecipientId.from(1)))
    composeTestRule.onNode(hasContentDescription("Delete") and hasAnyAncestor(hasTestTag(NotificationProfileDetailsTestTags.member(RecipientId.from(1))))).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.RemoveMemberClicked(RecipientId.from(1)))
  }

  @Test
  fun `see all is shown when members are hidden and emits SeeAllMembersClicked`() {
    setContent(loadedState(members = (1L..7L).map { recipient(it, "Member $it") }))

    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.member(RecipientId.from(6))).assertDoesNotExist()
    scrollTo(NotificationProfileDetailsTestTags.ROW_SEE_ALL)
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.ROW_SEE_ALL).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.SeeAllMembersClicked)
  }

  @Test
  fun `see all is hidden once every member is shown`() {
    setContent(loadedState(members = (1L..7L).map { recipient(it, "Member $it") }).copy(expanded = true))

    scrollTo(NotificationProfileDetailsTestTags.member(RecipientId.from(7)))
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.ROW_SEE_ALL).assertDoesNotExist()
  }

  @Test
  fun `the schedule row describes the schedule and emits ScheduleClicked`() {
    setContent(loadedState(schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 900, end = 1700, daysEnabled = DayOfWeek.entries.toSet())))

    scrollTo(NotificationProfileDetailsTestTags.ROW_SCHEDULE)
    composeTestRule.onNodeWithText("9:00 AM to 5:00 PM\nEveryday", substring = true).assertExists()
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.ROW_SCHEDULE).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.ScheduleClicked)
  }

  @Test
  fun `flipping allow all calls emits AllowAllCallsToggled with the new value`() {
    setContent(loadedState(allowAllCalls = true))

    scrollTo(NotificationProfileDetailsTestTags.ROW_ALLOW_ALL_CALLS)
    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(NotificationProfileDetailsTestTags.ROW_ALLOW_ALL_CALLS))).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.AllowAllCallsToggled(false))
  }

  @Test
  fun `flipping notify for all mentions emits NotifyForAllMentionsToggled with the new value`() {
    setContent(loadedState(allowAllMentions = false))

    scrollTo(NotificationProfileDetailsTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS)
    composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(NotificationProfileDetailsTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS))).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.NotifyForAllMentionsToggled(true))
  }

  @Test
  fun `clicking delete emits DeleteClicked`() {
    setContent(loadedState())

    scrollTo(NotificationProfileDetailsTestTags.ROW_DELETE)
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.ROW_DELETE).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.DeleteClicked)
  }

  @Test
  fun `confirming the delete dialog emits DeleteConfirmed`() {
    setContent(loadedState().copy(dialog = NotificationProfileDetailsState.Dialog.ConfirmDelete))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.DeleteConfirmed)
  }

  @Test
  fun `cancelling the delete dialog emits DialogDismissed`() {
    setContent(loadedState().copy(dialog = NotificationProfileDetailsState.Dialog.ConfirmDelete))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.DialogDismissed)
  }

  @Test
  fun `clicking back emits NavigateBackClicked`() {
    setContent(loadedState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(NotificationProfileDetailsEvent.NavigateBackClicked)
  }

  private fun setContent(state: NotificationProfileDetailsState) {
    composeTestRule.setContent {
      NotificationProfileDetailsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(NotificationProfileDetailsTestTags.LIST)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun loadedState(
    members: List<Recipient> = emptyList(),
    schedule: NotificationProfileSchedule = NotificationProfileSchedule(id = 1L),
    allowAllCalls: Boolean = true,
    allowAllMentions: Boolean = false
  ): NotificationProfileDetailsState {
    return NotificationProfileDetailsState(
      profile = NotificationProfile(
        id = 1L,
        name = "Work",
        emoji = "",
        createdAt = 0L,
        schedule = schedule,
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

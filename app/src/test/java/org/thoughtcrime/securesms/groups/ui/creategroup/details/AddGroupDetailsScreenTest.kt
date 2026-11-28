/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.recipients.Recipient

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddGroupDetailsScreenTest {

  companion object {
    private val MEMBERS = AddGroupDetailsPreviewData.members
    private val SAME_GROUPS = AddGroupDetailsPreviewData.sameGroups
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `simply rendering the screen emits no events`() {
    val events = setContent(createState())

    assertThat(events).isEmpty()
  }

  @Test
  fun `the title asks the user to name the group`() {
    setContent(createState())

    composeTestRule.onNodeWithText("Name this group").assertIsDisplayed()
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.NavigateBackClicked)
  }

  @Test
  fun `typing a name emits NameChanged`() {
    val events = setContent(createState(name = ""))

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.NAME_FIELD).performTextInput("Hikers")

    assertThat(events).contains(AddGroupDetailsEvent.NameChanged("Hikers"))
  }

  @Test
  fun `clicking the avatar emits AvatarClicked`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.AVATAR).performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.AvatarClicked)
  }

  @Test
  fun `clicking disappearing messages emits DisappearingMessagesClicked`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.DISAPPEARING_MESSAGES_ROW).performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.DisappearingMessagesClicked)
  }

  @Test
  fun `clicking a member emits MemberClicked`() {
    val events = setContent(createState())
    val member = MEMBERS.last()

    scrollTo(AddGroupDetailsTestTags.MEMBER_ROW + member.id.toLong())
    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.MEMBER_ROW + member.id.toLong()).performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.MemberClicked(member.id))
  }

  @Test
  fun `no members shows the add later hint`() {
    setContent(createState(members = emptyList()))

    scrollTo(AddGroupDetailsTestTags.ADD_LATER)
    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.ADD_LATER).assertIsDisplayed()
  }

  @Test
  fun `members hide the add later hint`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.ADD_LATER).assertDoesNotExist()
  }

  @Test
  fun `clicking a group with the same members emits SameGroupClicked`() {
    val events = setContent(createState(sameGroups = SAME_GROUPS))
    val group = SAME_GROUPS.first()

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.SAME_GROUP_CHIP + group.id.toLong()).performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.SameGroupClicked(group.id))
  }

  @Test
  fun `create is disabled without a name`() {
    setContent(createState(name = ""))

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.CREATE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `clicking create emits CreateClicked`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.CREATE_BUTTON)
      .assertIsEnabled()
      .performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.CreateClicked)
  }

  @Test
  fun `confirming the remove member dialog emits RemoveMemberConfirmed`() {
    val events = setContent(createState(dialog = AddGroupDetailsState.Dialog.ConfirmRemoveMember(MEMBERS.first())))

    composeTestRule.onNodeWithText("Remove").performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.RemoveMemberConfirmed)
  }

  @Test
  fun `cancelling a dialog emits DialogDismissed`() {
    val events = setContent(createState(dialog = AddGroupDetailsState.Dialog.ConfirmRemoveMember(MEMBERS.first())))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.DialogDismissed)
  }

  @Test
  fun `confirming the discard dialog emits DiscardConfirmed`() {
    val events = setContent(createState(sameGroups = SAME_GROUPS, dialog = AddGroupDetailsState.Dialog.ConfirmDiscard(SAME_GROUPS.first())))

    composeTestRule.onNodeWithText("Discard").performClick()

    assertThat(events).containsExactly(AddGroupDetailsEvent.DiscardConfirmed)
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AddGroupDetailsTestTags.SCROLLER).performScrollToNode(hasTestTag(testTag))
  }

  private fun setContent(state: AddGroupDetailsState): List<AddGroupDetailsEvent> {
    val emitted = mutableListOf<AddGroupDetailsEvent>()

    composeTestRule.setContent {
      // AvatarImage observes the live recipient outside of inspection mode, which needs the full app dependency graph.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        SignalTheme {
          AddGroupDetailsScreen(state = state, onEvent = { emitted += it })
        }
      }
    }

    return emitted
  }

  private fun createState(
    members: List<Recipient> = MEMBERS,
    name: String = "Hikers",
    sameGroups: List<Recipient> = emptyList(),
    dialog: AddGroupDetailsState.Dialog = AddGroupDetailsState.Dialog.None
  ): AddGroupDetailsState {
    return AddGroupDetailsState(
      members = members,
      name = name,
      sameGroups = sameGroups,
      dialog = dialog
    )
  }
}

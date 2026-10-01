/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.grouppermissions

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState.Dialog
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Stands in for a rejected change's message, which the view model resolves out of the app module. Deliberately a string
 * the screen never renders itself, so the snackbar assertions cannot match anything else on screen.
 */
private val ERROR_MESSAGE = android.R.string.ok

/**
 * Checks which events the permission rows emit, and that they only emit them for an admin of an active group.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GroupPermissionsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `allowing all members to add members emits SetNonAdminCanAddMembers`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true)))

    click(GroupPermissionsTestTags.ADD_MEMBERS_ROW)
    selectEditor(allMembers = true)

    assertThat(events).containsExactly(GroupPermissionsEvents.SetNonAdminCanAddMembers(true))
  }

  @Test
  fun `restricting members from adding members emits SetNonAdminCanAddMembers`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true, nonAdminCanAddMembers = true)))

    click(GroupPermissionsTestTags.ADD_MEMBERS_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(GroupPermissionsEvents.SetNonAdminCanAddMembers(false))
  }

  @Test
  fun `picking who can edit group info emits SetNonAdminCanEditGroupInfo`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true)))

    click(GroupPermissionsTestTags.EDIT_GROUP_INFO_ROW)
    selectEditor(allMembers = true)

    assertThat(events).containsExactly(GroupPermissionsEvents.SetNonAdminCanEditGroupInfo(true))
  }

  @Test
  fun `picking who can send messages emits SetNonAdminCanSendMessages`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true, nonAdminCanSendMessages = true)))

    click(GroupPermissionsTestTags.SEND_MESSAGES_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(GroupPermissionsEvents.SetNonAdminCanSendMessages(false))
  }

  @Test
  fun `picking who can add member labels emits SetNonAdminCanSetMemberLabel`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true, nonAdminCanSetMemberLabel = true)))

    click(GroupPermissionsTestTags.ADD_MEMBER_LABELS_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
  }

  @Test
  fun `picking an option without confirming it changes nothing`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true)))

    click(GroupPermissionsTestTags.ADD_MEMBERS_ROW)
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(1)).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `no row does anything for someone who cannot edit settings`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = false)))

    click(GroupPermissionsTestTags.ADD_MEMBERS_ROW)
    click(GroupPermissionsTestTags.EDIT_GROUP_INFO_ROW)
    click(GroupPermissionsTestTags.SEND_MESSAGES_ROW)
    click(GroupPermissionsTestTags.ADD_MEMBER_LABELS_ROW)

    assertThat(events).isEmpty()
  }

  @Test
  fun `confirming the member labels warning emits MemberLabelsWillBeClearedConfirmed`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true), dialog = Dialog.MemberLabelsWillBeCleared))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(
      GroupPermissionsEvents.DialogDismissed,
      GroupPermissionsEvents.MemberLabelsWillBeClearedConfirmed
    )
  }

  @Test
  fun `dismissing the member labels warning emits DialogDismissed`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true), dialog = Dialog.MemberLabelsWillBeCleared))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(GroupPermissionsEvents.DialogDismissed)
  }

  @Test
  fun `a rejected change is reported in a snackbar, which emits SnackbarDismissed once it has been seen`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true), errorMessage = ERROR_MESSAGE))

    composeTestRule.onNodeWithText(getString(ERROR_MESSAGE)).assertIsDisplayed()

    composeTestRule.waitUntil(timeoutMillis = 10_000) { events.isNotEmpty() }

    assertThat(events).containsExactly(GroupPermissionsEvents.SnackbarDismissed)
  }

  @Test
  fun `no snackbar shows while nothing has been rejected`() {
    val events = setContent(GroupPermissionsState(permissions = GroupPermissions.NONE.copy(selfCanEditSettings = true)))

    composeTestRule.onNodeWithText(getString(ERROR_MESSAGE)).assertDoesNotExist()

    assertThat(events).isEmpty()
  }

  private fun setContent(state: GroupPermissionsState): List<GroupPermissionsEvents> {
    val events = mutableListOf<GroupPermissionsEvents>()

    composeTestRule.setContent {
      SignalTheme {
        GroupPermissionsScreen(
          state = state,
          onEvent = { events += it },
          onNavigationClick = {}
        )
      }
    }

    return events
  }

  private fun getString(id: Int): String {
    return ApplicationProvider.getApplicationContext<Application>().getString(id)
  }

  private fun click(tag: String) {
    composeTestRule.onNodeWithTag(GroupPermissionsTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  /**
   * Picks an option out of an open editor dialog, whose options are ordered admins-only first, and confirms it. Every
   * one of these rows makes the user confirm before the change is applied.
   */
  private fun selectEditor(allMembers: Boolean) {
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(if (allMembers) 1 else 0)).performClick()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_RADIO_LIST_DIALOG_CONFIRM_BUTTON).performClick()
  }
}

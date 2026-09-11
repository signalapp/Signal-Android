/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

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
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.components.settings.conversation.permissions.PermissionsSettingsState.Dialog
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors

/**
 * Checks which events the permission rows emit, and that they only emit them for an admin of an active group.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PermissionsSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `allowing all members to add members emits SetNonAdminCanAddMembers`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true))

    click(PermissionsSettingsTestTags.ADD_MEMBERS_ROW)
    selectEditor(allMembers = true)

    assertThat(events).containsExactly(PermissionsSettingsEvents.SetNonAdminCanAddMembers(true))
  }

  @Test
  fun `restricting members from adding members emits SetNonAdminCanAddMembers`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, nonAdminCanAddMembers = true))

    click(PermissionsSettingsTestTags.ADD_MEMBERS_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(PermissionsSettingsEvents.SetNonAdminCanAddMembers(false))
  }

  @Test
  fun `picking who can edit group info emits SetNonAdminCanEditGroupInfo`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true))

    click(PermissionsSettingsTestTags.EDIT_GROUP_INFO_ROW)
    selectEditor(allMembers = true)

    assertThat(events).containsExactly(PermissionsSettingsEvents.SetNonAdminCanEditGroupInfo(true))
  }

  @Test
  fun `picking who can send messages emits SetNonAdminCanSendMessages`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, nonAdminCanSendMessages = true))

    click(PermissionsSettingsTestTags.SEND_MESSAGES_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(PermissionsSettingsEvents.SetNonAdminCanSendMessages(false))
  }

  @Test
  fun `picking who can add member labels emits SetNonAdminCanSetMemberLabel`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, nonAdminCanSetMemberLabel = true))

    click(PermissionsSettingsTestTags.ADD_MEMBER_LABELS_ROW)
    selectEditor(allMembers = false)

    assertThat(events).containsExactly(PermissionsSettingsEvents.SetNonAdminCanSetMemberLabel(false))
  }

  @Test
  fun `picking an option without confirming it changes nothing`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true))

    click(PermissionsSettingsTestTags.ADD_MEMBERS_ROW)
    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(1)).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `no row does anything for someone who cannot edit settings`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = false))

    click(PermissionsSettingsTestTags.ADD_MEMBERS_ROW)
    click(PermissionsSettingsTestTags.EDIT_GROUP_INFO_ROW)
    click(PermissionsSettingsTestTags.SEND_MESSAGES_ROW)
    click(PermissionsSettingsTestTags.ADD_MEMBER_LABELS_ROW)

    assertThat(events).isEmpty()
  }

  @Test
  fun `confirming the member labels warning emits MemberLabelsWillBeClearedConfirmed`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, dialog = Dialog.MemberLabelsWillBeCleared))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(
      PermissionsSettingsEvents.DialogDismissed,
      PermissionsSettingsEvents.MemberLabelsWillBeClearedConfirmed
    )
  }

  @Test
  fun `dismissing the member labels warning emits DialogDismissed`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, dialog = Dialog.MemberLabelsWillBeCleared))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(PermissionsSettingsEvents.DialogDismissed)
  }

  @Test
  fun `a rejected change is reported in a snackbar, which emits SnackbarDismissed once it has been seen`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true, groupChangeError = GroupChangeFailureReason.NO_RIGHTS))

    composeTestRule.onNodeWithText(failureMessage(GroupChangeFailureReason.NO_RIGHTS)).assertIsDisplayed()

    composeTestRule.waitUntil(timeoutMillis = 10_000) { events.isNotEmpty() }

    assertThat(events).containsExactly(PermissionsSettingsEvents.SnackbarDismissed)
  }

  @Test
  fun `no snackbar shows while nothing has been rejected`() {
    val events = setContent(PermissionsSettingsState(selfCanEditSettings = true))

    composeTestRule.onNodeWithText(failureMessage(GroupChangeFailureReason.NO_RIGHTS)).assertDoesNotExist()

    assertThat(events).isEmpty()
  }

  private fun setContent(state: PermissionsSettingsState): List<PermissionsSettingsEvents> {
    val events = mutableListOf<PermissionsSettingsEvents>()

    composeTestRule.setContent {
      SignalTheme {
        PermissionsSettingsScreen(
          state = state,
          onEvent = { events += it },
          onNavigationClick = {}
        )
      }
    }

    return events
  }

  private fun failureMessage(reason: GroupChangeFailureReason): String {
    return ApplicationProvider.getApplicationContext<Application>().getString(GroupErrors.getUserDisplayMessage(reason))
  }

  private fun click(tag: String) {
    composeTestRule.onNodeWithTag(PermissionsSettingsTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
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

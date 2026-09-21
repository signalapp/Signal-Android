/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.sharablegrouplink

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
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkState.Dialog
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Stands in for a rejected change's message, which the view model resolves out of the app module. Deliberately a string
 * the screen never renders itself, so the snackbar assertions cannot match anything else on screen.
 */
private val ERROR_MESSAGE = android.R.string.ok

/**
 * Checks which events the group link rows emit, and that they only emit them when the link is there to act on and the
 * user is allowed to act on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareableGroupLinkScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<ShareableGroupLinkEvents>()
  private var shareClicks = 0

  @Test
  fun `turning the link on emits GroupLinkToggled`() {
    setContent(ShareableGroupLinkState(groupLink = GroupLink.NONE.copy(selfCanEditSettings = true)))

    click(ShareableGroupLinkTestTags.GROUP_LINK_ROW)

    assertThat(events).containsExactly(ShareableGroupLinkEvents.GroupLinkToggled)
  }

  @Test
  fun `turning the link off emits GroupLinkToggled`() {
    setContent(enabledLinkState())

    click(ShareableGroupLinkTestTags.GROUP_LINK_ROW)

    assertThat(events).containsExactly(ShareableGroupLinkEvents.GroupLinkToggled)
  }

  @Test
  fun `an enabled link shows its url`() {
    setContent(enabledLinkState())

    composeTestRule.onNodeWithText(GROUP_LINK_URL).assertIsDisplayed()
  }

  @Test
  fun `a disabled link shows no url`() {
    setContent(ShareableGroupLinkState(groupLink = GroupLink.NONE.copy(url = GROUP_LINK_URL, selfCanEditSettings = true)))

    composeTestRule.onNodeWithText(GROUP_LINK_URL).assertDoesNotExist()
  }

  @Test
  fun `sharing the link is left to the caller`() {
    setContent(enabledLinkState())

    click(ShareableGroupLinkTestTags.SHARE_ROW)

    assertThat(shareClicks).isEqualTo(1)
    assertThat(events).isEmpty()
  }

  @Test
  fun `resetting the link emits ResetLinkClicked`() {
    setContent(enabledLinkState())

    click(ShareableGroupLinkTestTags.RESET_LINK_ROW)

    assertThat(events).containsExactly(ShareableGroupLinkEvents.ResetLinkClicked)
  }

  @Test
  fun `requiring admin approval emits AdminApprovalToggled`() {
    setContent(enabledLinkState())

    click(ShareableGroupLinkTestTags.ADMIN_APPROVAL_ROW)

    assertThat(events).containsExactly(ShareableGroupLinkEvents.AdminApprovalToggled)
  }

  @Test
  fun `no row does anything while the link is off`() {
    setContent(ShareableGroupLinkState(groupLink = GroupLink.NONE.copy(selfCanEditSettings = true)))

    click(ShareableGroupLinkTestTags.SHARE_ROW)
    click(ShareableGroupLinkTestTags.RESET_LINK_ROW)
    click(ShareableGroupLinkTestTags.ADMIN_APPROVAL_ROW)

    assertThat(shareClicks).isEqualTo(0)
    assertThat(events).isEmpty()
  }

  @Test
  fun `a non-admin can share the link but not change it`() {
    setContent(enabledLinkState(selfCanEditSettings = false))

    click(ShareableGroupLinkTestTags.GROUP_LINK_ROW)
    click(ShareableGroupLinkTestTags.RESET_LINK_ROW)
    click(ShareableGroupLinkTestTags.ADMIN_APPROVAL_ROW)
    click(ShareableGroupLinkTestTags.SHARE_ROW)

    assertThat(shareClicks).isEqualTo(1)
    assertThat(events).isEmpty()
  }

  @Test
  fun `confirming the reset warning emits ResetLinkConfirmed`() {
    setContent(enabledLinkState(dialog = Dialog.ConfirmResetLink))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).containsExactly(
      ShareableGroupLinkEvents.DialogDismissed,
      ShareableGroupLinkEvents.ResetLinkConfirmed
    )
  }

  @Test
  fun `dismissing the reset warning emits DialogDismissed`() {
    setContent(enabledLinkState(dialog = Dialog.ConfirmResetLink))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).containsExactly(ShareableGroupLinkEvents.DialogDismissed)
  }

  @Test
  fun `a rejected change is reported in a snackbar, which emits SnackbarDismissed once it has been seen`() {
    setContent(enabledLinkState(errorMessage = ERROR_MESSAGE))

    composeTestRule.onNodeWithText(getString(ERROR_MESSAGE)).assertIsDisplayed()

    composeTestRule.waitUntil(timeoutMillis = 10_000) { events.isNotEmpty() }

    assertThat(events).containsExactly(ShareableGroupLinkEvents.SnackbarDismissed)
  }

  @Test
  fun `no snackbar shows while nothing has been rejected`() {
    setContent(enabledLinkState())

    composeTestRule.onNodeWithText(getString(ERROR_MESSAGE)).assertDoesNotExist()

    assertThat(events).isEmpty()
  }

  private fun setContent(state: ShareableGroupLinkState) {
    composeTestRule.setContent {
      SignalTheme {
        ShareableGroupLinkScreen(
          state = state,
          onEvent = { events += it },
          onShareClick = { shareClicks++ },
          onNavigationClick = {}
        )
      }
    }
  }

  private fun enabledLinkState(
    selfCanEditSettings: Boolean = true,
    dialog: Dialog = Dialog.None,
    errorMessage: Int? = null
  ): ShareableGroupLinkState {
    return ShareableGroupLinkState(
      groupLink = GroupLink.NONE.copy(
        enabled = true,
        url = GROUP_LINK_URL,
        selfCanEditSettings = selfCanEditSettings
      ),
      dialog = dialog,
      errorMessage = errorMessage
    )
  }

  private fun getString(id: Int): String {
    return ApplicationProvider.getApplicationContext<Application>().getString(id)
  }

  private fun click(tag: String) {
    composeTestRule.onNodeWithTag(ShareableGroupLinkTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  companion object {
    private const val GROUP_LINK_URL = "https://signal.group/#CjQKIP_ZZ3Zz"
  }
}

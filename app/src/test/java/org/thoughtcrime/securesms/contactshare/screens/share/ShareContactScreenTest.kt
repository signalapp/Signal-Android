/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactState.DetailLabel
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactState.DetailSelection

/**
 * Checks which events the share screen's rows emit and what it renders for a given selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareContactScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `tapping a detail row toggles that detail`() {
    val events = setContent(createState())

    scrollTo(ShareContactTestTags.detailRow("email:0"))
    composeTestRule.onNodeWithTag(ShareContactTestTags.detailRow("email:0")).performClick()

    assertThat(events).containsExactly(ShareContactEvent.DetailToggled("email:0"))
  }

  @Test
  fun `the edit name button emits EditNameClicked`() {
    val events = setContent(createState())

    scrollTo(ShareContactTestTags.EDIT_NAME_BUTTON)
    composeTestRule.onNodeWithTag(ShareContactTestTags.EDIT_NAME_BUTTON).performClick()

    assertThat(events).containsExactly(ShareContactEvent.EditNameClicked)
  }

  @Test
  fun `the name row does not toggle while the name is required`() {
    val events = setContent(createState(nameToggleable = false))

    scrollTo(ShareContactTestTags.NAME_ROW)
    composeTestRule.onNodeWithTag(ShareContactTestTags.NAME_ROW).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `the name row toggles once it is allowed to`() {
    val events = setContent(createState(nameToggleable = true))

    scrollTo(ShareContactTestTags.NAME_ROW)
    composeTestRule.onNodeWithTag(ShareContactTestTags.NAME_ROW).performClick()

    assertThat(events).containsExactly(ShareContactEvent.NameToggled)
  }

  @Test
  fun `send emits SendClicked when there is a name to send`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithTag(ShareContactTestTags.SEND_BUTTON).performClick()

    assertThat(events).containsExactly(ShareContactEvent.SendClicked)
  }

  @Test
  fun `send does nothing without a selected name`() {
    val events = setContent(createState(nameSelected = false, nameToggleable = true))

    composeTestRule.onNodeWithTag(ShareContactTestTags.SEND_BUTTON).performClick()

    assertThat(events).isEmpty()
  }

  @Test
  fun `the photo edit badge only renders when there is more than one photo`() {
    setContent(createState(photoEditable = true))
    composeTestRule.onNodeWithTag(ShareContactTestTags.EDIT_PHOTO_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `the photo edit badge is hidden with a single photo`() {
    setContent(createState(photoEditable = false))

    assertThat(composeTestRule.onAllNodesWithTag(ShareContactTestTags.EDIT_PHOTO_BUTTON).fetchSemanticsNodes().toList()).isEmpty()
  }

  private fun scrollTo(tag: String) {
    composeTestRule.onNodeWithTag(ShareContactTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
  }

  private fun setContent(state: ShareContactState): List<ShareContactEvent> {
    val emitted = mutableListOf<ShareContactEvent>()

    composeTestRule.setContent {
      SignalTheme {
        ShareContactScreen(state = state, onEvent = { emitted += it })
      }
    }

    return emitted
  }

  private fun createState(
    nameSelected: Boolean = true,
    nameToggleable: Boolean = false,
    photoEditable: Boolean = true
  ): ShareContactState {
    return ShareContactState(
      sendingTo = "Maya Johnson",
      avatar = ShareContactState.AvatarSelection(
        isSelected = true,
        photo = ShareContactState.ContactPhoto(uri = "content://photo", isProfile = false),
        isEditable = photoEditable
      ),
      name = ShareContactState.NameSelection(
        displayName = "Paige Hall",
        isSelected = nameSelected,
        isEditable = true,
        isToggleable = nameToggleable
      ),
      details = listOf(
        DetailSelection("phone:0", listOf("+1 510-123-4567"), DetailLabel.Text("Mobile"), isSelected = true),
        DetailSelection("email:0", listOf("paigehall@example.com"), DetailLabel.Text("Home"), isSelected = false)
      )
    )
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.Dialogs
import org.signal.emoji.EmojiDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditProfileScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  /** The rows render the name and about with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<EditProfileEvent>()

  /** Everything but the [EditProfileEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<EditProfileEvent>
    get() = events.filterNot { it == EditProfileEvent.ScreenResumed }

  private val populated = EditProfileState(profileName = "Alex Hart", about = "Speak freely", aboutEmoji = "👋", username = "spider.42")

  @Test
  fun whenIDisplayTheScreen_thenIExpectOnlyScreenResumed() {
    setContent(populated)

    assertThat(events).containsExactly(EditProfileEvent.ScreenResumed)
  }

  @Test
  fun whenIClickEachRow_thenIExpectTheMatchingEvents() {
    setContent(populated)

    composeTestRule.onNodeWithTag(EditProfileTestTags.AVATAR).performClick()
    composeTestRule.onNodeWithTag(EditProfileTestTags.BUTTON_EDIT_PHOTO).performClick()
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_NAME).performClick()
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_ABOUT).performClick()
    scrollTo(EditProfileTestTags.ROW_BADGES)
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_BADGES).performClick()
    scrollTo(EditProfileTestTags.ROW_USERNAME)
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_USERNAME).performClick()
    scrollTo(EditProfileTestTags.ROW_USERNAME_LINK)
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_USERNAME_LINK).performClick()

    assertThat(interactionEvents).containsExactly(
      EditProfileEvent.AvatarClicked,
      EditProfileEvent.EditPhotoClicked,
      EditProfileEvent.NameClicked,
      EditProfileEvent.AboutClicked,
      EditProfileEvent.BadgesClicked,
      EditProfileEvent.UsernameClicked,
      EditProfileEvent.UsernameLinkClicked
    )
  }

  @Test
  fun givenAnEmptyProfile_whenIDisplayScreen_thenIExpectPlaceholders() {
    setContent(EditProfileState())

    composeTestRule.onNodeWithText("Profile name").assertExists()
    composeTestRule.onNodeWithText("About").assertExists()
    scrollTo(EditProfileTestTags.ROW_USERNAME)
    composeTestRule.onNodeWithText("Username").assertExists()
  }

  @Test
  fun givenNoUsername_whenIDisplayScreen_thenIExpectNoLinkRow() {
    setContent(populated.copy(username = null))

    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_USERNAME_LINK).assertDoesNotExist()
  }

  @Test
  fun givenABrokenUsername_whenIDisplayScreen_thenIExpectAnErrorAndNoLinkRow() {
    setContent(populated.copy(usernameSyncState = EditProfileState.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED))

    scrollTo(EditProfileTestTags.ROW_USERNAME)
    composeTestRule.onNodeWithTag(EditProfileTestTags.USERNAME_ERROR, useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_USERNAME_LINK).assertDoesNotExist()
  }

  @Test
  fun givenABrokenLink_whenIDisplayScreen_thenIExpectAnErrorOnTheLinkRow() {
    setContent(populated.copy(usernameSyncState = EditProfileState.UsernameSyncState.LINK_CORRUPTED))

    scrollTo(EditProfileTestTags.ROW_USERNAME_LINK)
    composeTestRule.onNodeWithTag(EditProfileTestTags.USERNAME_LINK_ERROR, useUnmergedTree = true).assertExists()
  }

  @Test
  fun givenTheTooltip_whenIClose_thenIExpectTooltipDismissedEvent() {
    setContent(populated.copy(showUsernameLinkTooltip = true))

    scrollTo(EditProfileTestTags.TOOLTIP)
    composeTestRule.onNodeWithTag(EditProfileTestTags.BUTTON_TOOLTIP_CLOSE).performClick()

    assertThat(events).contains(EditProfileEvent.UsernameLinkTooltipDismissed)
  }

  @Test
  fun givenTheProfileCantBeEdited_whenIDisplayScreen_thenIExpectEditPhotoDisabled() {
    setContent(populated.copy(canEdit = false))

    composeTestRule.onNodeWithTag(EditProfileTestTags.BUTTON_EDIT_PHOTO).assertIsNotEnabled()
  }

  @Test
  fun givenTheProfileCantBeEdited_whenIClickARow_thenIExpectItsEventSoTheUserIsToldWhy() {
    setContent(populated.copy(canEdit = false))

    composeTestRule.onNodeWithTag(EditProfileTestTags.ROW_NAME).performClick()

    assertThat(events).contains(EditProfileEvent.NameClicked)
  }

  @Test
  fun givenTheUsernameOptions_whenIChooseEachOption_thenIExpectItsEvent() {
    setContent(populated.copy(dialog = EditProfileState.Dialog.UsernameOptions))

    composeTestRule.onNodeWithTag(Dialogs.testTagListDialogOption(0)).performClick()
    composeTestRule.onNodeWithTag(Dialogs.testTagListDialogOption(1)).performClick()

    assertThat(interactionEvents).containsExactly(EditProfileEvent.EditUsernameClicked, EditProfileEvent.DeleteUsernameClicked)
  }

  @Test
  fun givenTheDeleteConfirmation_whenIDelete_thenIExpectDeleteUsernameConfirmedEvent() {
    setContent(populated.copy(dialog = EditProfileState.Dialog.ConfirmDeleteUsername("spider.42")))

    composeTestRule.onNodeWithText("Delete").performClick()

    assertThat(events).contains(EditProfileEvent.DeleteUsernameConfirmed)
  }

  @Test
  fun givenTheBecomeASustainerSheet_whenIClickTheButton_thenIExpectBecomeASustainerClickedEvent() {
    setContent(populated.copy(dialog = EditProfileState.Dialog.BecomeASustainer))

    composeTestRule.onNodeWithTag(EditProfileTestTags.BUTTON_BECOME_A_SUSTAINER).performClick()

    assertThat(events).contains(EditProfileEvent.BecomeASustainerClicked)
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(EditProfileTestTags.LIST).performScrollToNode(hasTestTag(testTag))
  }

  private fun setContent(state: EditProfileState) {
    composeTestRule.setContent {
      EditProfileScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}

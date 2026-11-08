/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UsernameEditScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<UsernameEditEvent>()

  private val reserved = UsernameEditState(
    nickname = "spider",
    discriminator = "42",
    showDiscriminator = true,
    username = "spider.42",
    button = UsernameEditState.Button.SUBMIT
  )

  @Test
  fun whenITypeANickname_thenIExpectNicknameChangedEvent() {
    setContent(UsernameEditState())

    nicknameInput().performTextReplacement("spider")
    composeTestRule.waitForIdle()

    assertThat(events).contains(UsernameEditEvent.NicknameChanged("spider"))
  }

  @Test
  fun givenStateThatLagsBehindTyping_whenAnOlderNicknameComesBack_thenIExpectTheFieldToKeepWhatWasTyped() {
    var state by mutableStateOf(UsernameEditState())
    composeTestRule.setContent {
      UsernameEditScreen(state = state, onEvent = { events += it })
    }

    nicknameInput().performTextReplacement("sp")
    composeTestRule.waitForIdle()
    nicknameInput().performTextReplacement("spider")
    composeTestRule.waitForIdle()

    state = UsernameEditState(nickname = "sp")
    composeTestRule.waitForIdle()

    nicknameInput().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("spider")))
  }

  @Test
  fun givenANicknameTheViewModelSets_whenIDisplayIt_thenIExpectTheFieldToShowIt() {
    var state by mutableStateOf(UsernameEditState())
    composeTestRule.setContent {
      UsernameEditScreen(state = state, onEvent = { events += it })
    }

    state = UsernameEditState(nickname = "venom")
    composeTestRule.waitForIdle()

    nicknameInput().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("venom")))
  }

  @Test
  fun whenITypeADiscriminator_thenIExpectDiscriminatorChangedEvent() {
    setContent(reserved)

    composeTestRule.onNodeWithTag(UsernameEditTestTags.DISCRIMINATOR_INPUT).performTextReplacement("77")

    assertThat(events).contains(UsernameEditEvent.DiscriminatorChanged("77"))
  }

  @Test
  fun givenNoDiscriminator_whenIDisplayScreen_thenIExpectNoDiscriminatorField() {
    setContent(UsernameEditState(nickname = "spider"))

    composeTestRule.onNodeWithTag(UsernameEditTestTags.DISCRIMINATOR_INPUT).assertDoesNotExist()
  }

  @Test
  fun givenAReservation_whenIDisplayScreen_thenIExpectTheReservedUsername() {
    setContent(reserved)

    composeTestRule.onNodeWithTag(UsernameEditTestTags.USERNAME).assertTextEquals("spider.42")
  }

  @Test
  fun givenNoUsername_whenIDisplayScreen_thenIExpectAPromptToChooseOne() {
    setContent(UsernameEditState())

    composeTestRule.onNodeWithTag(UsernameEditTestTags.USERNAME).assertTextEquals("Choose your username")
  }

  @Test
  fun givenAReservationInFlight_whenIDisplayScreen_thenIExpectASpinner() {
    setContent(UsernameEditState(nickname = "spider", reserving = true))

    composeTestRule.onNodeWithTag(UsernameEditTestTags.RESERVING).assertExists()
  }

  @Test
  fun givenAnError_whenIDisplayScreen_thenIExpectItBelowTheField() {
    setContent(UsernameEditState(nickname = "1spider", error = UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER))

    composeTestRule.onNodeWithTag(UsernameEditTestTags.ERROR, useUnmergedTree = true).assertTextEquals("Usernames cannot begin with a number.")
  }

  @Test
  fun givenAReservation_whenIClickSave_thenIExpectSubmitClickedEvent() {
    setContent(reserved)

    composeTestRule.onNodeWithTag(UsernameEditTestTags.BUTTON_SUBMIT)
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(UsernameEditEvent.SubmitClicked)
  }

  @Test
  fun givenNoReservation_whenIDisplayScreen_thenIExpectSaveDisabled() {
    setContent(UsernameEditState(nickname = "spider"))

    composeTestRule.onNodeWithTag(UsernameEditTestTags.BUTTON_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun givenAClearedUsername_whenIClickDelete_thenIExpectDeleteClickedEvent() {
    setContent(UsernameEditState(discriminator = "42", showDiscriminator = true, button = UsernameEditState.Button.DELETE))

    composeTestRule.onNodeWithTag(UsernameEditTestTags.BUTTON_SUBMIT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(UsernameEditTestTags.BUTTON_DELETE).performClick()

    assertThat(events).contains(UsernameEditEvent.DeleteClicked)
  }

  @Test
  fun givenASaveInFlight_whenIDisplayScreen_thenIExpectTheFieldsDisabled() {
    setContent(reserved.copy(button = UsernameEditState.Button.SUBMIT_LOADING))

    nicknameInput().assertIsNotEnabled()
    composeTestRule.onNodeWithTag(UsernameEditTestTags.DISCRIMINATOR_INPUT).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(UsernameEditTestTags.BUTTON_SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun givenTheResetConfirmation_whenIContinue_thenIExpectResetConfirmedEvent() {
    setContent(reserved.copy(dialog = UsernameEditState.Dialog.ConfirmReset))

    composeTestRule.onNodeWithText("Continue").performClick()

    assertThat(events).contains(UsernameEditEvent.ResetConfirmed)
  }

  @Test
  fun givenTheResetConfirmation_whenICancel_thenIExpectDialogDismissedEvent() {
    setContent(reserved.copy(dialog = UsernameEditState.Dialog.ConfirmReset))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).contains(UsernameEditEvent.DialogDismissed)
  }

  @Test
  fun givenTheRecoveryConfirmation_whenIConfirm_thenIExpectSameUsernameRecoveryConfirmedEvent() {
    setContent(reserved.copy(dialog = UsernameEditState.Dialog.ConfirmSameUsernameRecovery))

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).contains(UsernameEditEvent.SameUsernameRecoveryConfirmed)
  }

  /** The editable node inside the nickname field; the tag sits on the field's decoration around it. */
  private fun nicknameInput(): SemanticsNodeInteraction {
    return composeTestRule.onNode(
      SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
        (hasTestTag(UsernameEditTestTags.NICKNAME_INPUT) or hasAnyAncestor(hasTestTag(UsernameEditTestTags.NICKNAME_INPUT))) and
        !hasTestTag(UsernameEditTestTags.DISCRIMINATOR_INPUT)
    )
  }

  private fun setContent(state: UsernameEditState) {
    composeTestRule.setContent {
      UsernameEditScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}

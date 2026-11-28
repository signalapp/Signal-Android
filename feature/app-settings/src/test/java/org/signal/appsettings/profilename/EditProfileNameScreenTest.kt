/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.profilename

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditProfileNameScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<EditProfileNameEvent>()

  @Test
  fun whenITypeAFirstName_thenIExpectGivenNameChangedEvent() {
    setContent(EditProfileNameState())

    input(EditProfileNameTestTags.GIVEN_NAME_INPUT).performTextReplacement("Alex")

    assertThat(events).contains(EditProfileNameEvent.GivenNameChanged("Alex"))
  }

  @Test
  fun whenITypeALastName_thenIExpectFamilyNameChangedEvent() {
    setContent(EditProfileNameState(givenName = "Alex"))

    input(EditProfileNameTestTags.FAMILY_NAME_INPUT).performTextReplacement("Hart")

    assertThat(events).contains(EditProfileNameEvent.FamilyNameChanged("Hart"))
  }

  @Test
  fun givenABlankFirstName_whenIDisplayScreen_thenIExpectSaveDisabled() {
    setContent(EditProfileNameState(familyName = "Hart"))

    composeTestRule.onNodeWithTag(EditProfileNameTestTags.BUTTON_SAVE).assertIsNotEnabled()
  }

  @Test
  fun givenASavingState_whenIDisplayScreen_thenIExpectSaveAndFieldsDisabled() {
    setContent(EditProfileNameState(givenName = "Alex", saving = true))

    composeTestRule.onNodeWithTag(EditProfileNameTestTags.BUTTON_SAVE).assertIsNotEnabled()
    input(EditProfileNameTestTags.GIVEN_NAME_INPUT).assertIsNotEnabled()
    input(EditProfileNameTestTags.FAMILY_NAME_INPUT).assertIsNotEnabled()
  }

  @Test
  fun givenAFirstName_whenIClickSave_thenIExpectSaveClickedEvent() {
    setContent(EditProfileNameState(givenName = "Alex"))

    composeTestRule.onNodeWithTag(EditProfileNameTestTags.BUTTON_SAVE)
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(EditProfileNameEvent.SaveClicked)
  }

  /** The editable node inside the field tagged [tag]; the tag sits on the field's decoration around it. */
  private fun input(tag: String): SemanticsNodeInteraction {
    return composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasAnyAncestor(hasTestTag(tag)))
  }

  private fun setContent(state: EditProfileNameState) {
    composeTestRule.setContent {
      EditProfileNameScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }
}

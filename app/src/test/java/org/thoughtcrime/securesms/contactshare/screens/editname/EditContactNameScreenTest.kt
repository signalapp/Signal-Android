/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Checks that each name field emits the event for its own part, and that saving is gated on there
 * being a name worth keeping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditContactNameScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `each field emits the event for its own part`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithTag(EditContactNameTestTags.PREFIX_FIELD).performTextReplacement("Dr")
    composeTestRule.onNodeWithTag(EditContactNameTestTags.GIVEN_NAME_FIELD).performTextReplacement("Paige")
    composeTestRule.onNodeWithTag(EditContactNameTestTags.MIDDLE_NAME_FIELD).performTextReplacement("A")
    composeTestRule.onNodeWithTag(EditContactNameTestTags.FAMILY_NAME_FIELD).performTextReplacement("Hall")
    composeTestRule.onNodeWithTag(EditContactNameTestTags.SUFFIX_FIELD).performTextReplacement("II")

    assertThat(events).containsExactly(
      EditContactNameEvent.PrefixChanged("Dr"),
      EditContactNameEvent.GivenNameChanged("Paige"),
      EditContactNameEvent.MiddleNameChanged("A"),
      EditContactNameEvent.FamilyNameChanged("Hall"),
      EditContactNameEvent.SuffixChanged("II")
    )
  }

  @Test
  fun `done emits SaveClicked`() {
    val events = setContent(createState(givenName = "Paige"))

    composeTestRule.onNodeWithTag(EditContactNameTestTags.DONE_BUTTON).assertIsEnabled().performClick()

    assertThat(events).containsExactly(EditContactNameEvent.SaveClicked)
  }

  @Test
  fun `done is disabled once the name has been cleared`() {
    setContent(createState(original = ContactNameParts(givenName = "Paige")))

    composeTestRule.onNodeWithTag(EditContactNameTestTags.DONE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `done is disabled while nothing has been edited`() {
    val unchanged = ContactNameParts(givenName = "Paige")

    setContent(createState(givenName = "Paige", original = unchanged))

    composeTestRule.onNodeWithTag(EditContactNameTestTags.DONE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `a company only card can still be saved`() {
    setContent(createState(organization = "Signal Messenger"))

    composeTestRule.onNodeWithTag(EditContactNameTestTags.DONE_BUTTON).assertIsEnabled()
  }

  private fun setContent(state: EditContactNameState): List<EditContactNameEvent> {
    val emitted = mutableListOf<EditContactNameEvent>()

    composeTestRule.setContent {
      SignalTheme {
        EditContactNameScreen(state = state, onEvent = { emitted += it })
      }
    }

    return emitted
  }

  /** [original] defaults to an empty name, so anything passed in counts as an edit. */
  private fun createState(
    givenName: String = "",
    familyName: String = "",
    organization: String = "",
    original: ContactNameParts = ContactNameParts()
  ): EditContactNameState {
    val parts = ContactNameParts(givenName = givenName, familyName = familyName, organization = organization)

    return EditContactNameState(parts = parts, original = original)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.hiddencontacts

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HiddenContactsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<HiddenContactsEvent>()

  /** Everything but the [HiddenContactsEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<HiddenContactsEvent>
    get() = events.filterNot { it == HiddenContactsEvent.ScreenResumed }

  private val alice = HiddenContactsState.Contact(RecipientId.from(1), "Alice")
  private val bob = HiddenContactsState.Contact(RecipientId.from(2), "Bob")

  @Test
  fun `resuming the screen emits ScreenResumed and nothing else`() {
    setContent(createState())

    assertThat(events).containsExactly(HiddenContactsEvent.ScreenResumed)
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(interactionEvents).containsExactly(HiddenContactsEvent.NavigateBackClicked)
  }

  @Test
  fun `every hidden contact is listed by name`() {
    setContent(createState())

    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bob").assertIsDisplayed()
    composeTestRule.onNodeWithTag(HiddenContactsTestTags.TEXT_EMPTY).assertDoesNotExist()
  }

  @Test
  fun `clicking a contact emits ContactClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(HiddenContactsTestTags.ROW_CONTACT + 2).performClick()

    assertThat(interactionEvents).containsExactly(HiddenContactsEvent.ContactClicked(bob.id))
  }

  @Test
  fun `the empty state is shown when nobody is hidden`() {
    setContent(createState(contacts = emptyList()))

    composeTestRule.onNodeWithTag(HiddenContactsTestTags.TEXT_EMPTY).assertIsDisplayed()
  }

  @Test
  fun `the empty state is not shown while loading`() {
    setContent(createState(contacts = emptyList(), isLoading = true))

    composeTestRule.onNodeWithTag(HiddenContactsTestTags.TEXT_EMPTY).assertDoesNotExist()
  }

  @Test
  fun `confirming the unhide dialog emits UnhideConfirmed`() {
    setContent(createState(dialog = HiddenContactsState.Dialog.ConfirmUnhide(alice)))

    composeTestRule.onNodeWithText("Unhide Alice?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unhide").performClick()

    assertThat(interactionEvents).containsExactly(HiddenContactsEvent.UnhideConfirmed)
  }

  @Test
  fun `cancelling the unhide dialog emits DialogDismissed`() {
    setContent(createState(dialog = HiddenContactsState.Dialog.ConfirmUnhide(alice)))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(interactionEvents).containsExactly(HiddenContactsEvent.DialogDismissed)
  }

  private fun setContent(state: HiddenContactsState) {
    composeTestRule.setContent {
      HiddenContactsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun createState(
    contacts: List<HiddenContactsState.Contact> = listOf(alice, bob),
    isLoading: Boolean = false,
    dialog: HiddenContactsState.Dialog = HiddenContactsState.Dialog.None
  ): HiddenContactsState {
    return HiddenContactsState(
      contacts = contacts,
      isLoading = isLoading,
      dialog = dialog
    )
  }
}

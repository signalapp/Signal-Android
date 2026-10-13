/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContactAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContextMenu
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailKind
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailRow
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Checks what the details screen renders for a given card and which events its controls emit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedContactDetailsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `pressing a detail row emits DetailPressed`() {
    val events = setContent(createState())

    scrollTo(SharedContactDetailsTestTags.detailRow("phone:0"))
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.detailRow("phone:0")).performClick()

    assertThat(events).containsExactly(SharedContactDetailsEvent.DetailPressed("phone:0"))
  }

  @Test
  fun `tapping an action row emits ActionClicked for that row`() {
    val events = setContent(createState())

    scrollTo(SharedContactDetailsTestTags.actionRow(ContactAction.ADD_TO_PHONE_CONTACTS))
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.actionRow(ContactAction.ADD_TO_PHONE_CONTACTS)).performClick()

    assertThat(events).containsExactly(SharedContactDetailsEvent.ActionClicked(ContactAction.ADD_TO_PHONE_CONTACTS))
  }

  @Test
  fun `call buttons only render for a contact that can be reached`() {
    setContent(createState(isOnSignal = true))

    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.MESSAGE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.VIDEO_CALL_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.AUDIO_CALL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `call buttons are hidden when no number matched a recipient`() {
    setContent(createState(isOnSignal = false))

    assertNotRendered(SharedContactDetailsTestTags.MESSAGE_BUTTON)
    assertNotRendered(SharedContactDetailsTestTags.VIDEO_CALL_BUTTON)
    assertNotRendered(SharedContactDetailsTestTags.AUDIO_CALL_BUTTON)
  }

  @Test
  fun `the call buttons emit their own events`() {
    val events = setContent(createState(isOnSignal = true))

    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.MESSAGE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.VIDEO_CALL_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.AUDIO_CALL_BUTTON).performClick()

    assertThat(events).containsExactly(
      SharedContactDetailsEvent.MessageClicked,
      SharedContactDetailsEvent.VideoCallClicked,
      SharedContactDetailsEvent.AudioCallClicked
    )
  }

  @Test
  fun `an open menu renders its entries and they emit DetailActionClicked`() {
    val events = setContent(
      createState(
        contextMenu = ContextMenu(
          detailId = "address:0",
          actions = listOf(DetailAction.OPEN_IN_MAPS, DetailAction.COPY)
        )
      )
    )

    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.menuItem(DetailAction.OPEN_IN_MAPS)).performClick()

    assertThat(events).containsExactly(SharedContactDetailsEvent.DetailActionClicked(DetailAction.OPEN_IN_MAPS))
  }

  @Test
  fun `the company renders under the name rather than as a row`() {
    setContent(createState(organization = "Signal Messenger"))

    composeTestRule.onNodeWithText("Signal Messenger").assertIsDisplayed()
  }

  @Test
  fun `a loading card renders no rows`() {
    setContent(SharedContactDetailsState(isLoading = true))

    assertNotRendered(SharedContactDetailsTestTags.CONTENT)
  }

  private fun scrollTo(tag: String) {
    composeTestRule.onNodeWithTag(SharedContactDetailsTestTags.CONTENT).performScrollToNode(hasTestTag(tag))
  }

  private fun assertNotRendered(tag: String) {
    assertThat(composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().toList()).isEmpty()
  }

  private fun setContent(state: SharedContactDetailsState): List<SharedContactDetailsEvent> {
    val emitted = mutableListOf<SharedContactDetailsEvent>()

    composeTestRule.setContent {
      SignalTheme {
        SharedContactDetailsScreen(state = state, onEvent = { emitted += it })
      }
    }

    return emitted
  }

  private fun createState(
    isOnSignal: Boolean = false,
    organization: String? = null,
    contextMenu: ContextMenu? = null
  ): SharedContactDetailsState {
    return SharedContactDetailsState(
      displayName = "Paige Hall",
      organization = organization,
      signalRecipientId = if (isOnSignal) RecipientId.from(1L) else null,
      actions = listOf(ContactAction.ADD_TO_PHONE_CONTACTS),
      details = listOf(
        DetailRow("phone:0", listOf("+1 510-123-4567"), "Mobile", DetailKind.PHONE),
        DetailRow("address:0", listOf("123 Beach Drive", "San Francisco CA"), "Home", DetailKind.ADDRESS)
      ),
      contextMenu = contextMenu
    )
  }
}

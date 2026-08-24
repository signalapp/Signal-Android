/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.signal.registration.test.TestTags

/**
 * Tests for MessageSyncScreen that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MessageSyncScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays cancel button`() {
    composeTestRule.setContent {
      SignalTheme {
        MessageSyncScreen(
          state = MessageSyncScreenState(stage = MessageSyncScreenState.Stage.Downloading(downloaded = 1.mebiBytes, total = 3300.kibiBytes)),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.MESSAGE_SYNC_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MESSAGE_SYNC_CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Cancel is clicked, CancelClick event is emitted`() {
    var emittedEvent: MessageSyncScreenEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        MessageSyncScreen(
          state = MessageSyncScreenState(stage = MessageSyncScreenState.Stage.Downloading(downloaded = 1.mebiBytes, total = 3300.kibiBytes)),
          onEvent = { event -> emittedEvent = event }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.MESSAGE_SYNC_CANCEL_BUTTON).performClick()

    assert(emittedEvent == MessageSyncScreenEvent.CancelClick)
  }

  @Test
  fun `learn more link is displayed`() {
    composeTestRule.setContent {
      SignalTheme {
        MessageSyncScreen(
          state = MessageSyncScreenState(stage = MessageSyncScreenState.Stage.Downloading(downloaded = 1.mebiBytes, total = 3300.kibiBytes)),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.MESSAGE_SYNC_LEARN_MORE_LINK).assertIsDisplayed()
  }

  @Test
  fun `when finishing, Cancel button is disabled`() {
    composeTestRule.setContent {
      SignalTheme {
        MessageSyncScreen(
          state = MessageSyncScreenState(stage = MessageSyncScreenState.Stage.Finishing),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.MESSAGE_SYNC_CANCEL_BUTTON).assertIsNotEnabled()
  }
}

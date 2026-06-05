/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.screens.quickrestore.QrState
import org.signal.registration.test.TestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LinkAccountScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `when Get Help is clicked, GetHelpClick event is emitted`() {
    var emittedEvent: LinkAccountScreenEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        LinkAccountScreen(
          state = LinkAccountScreenState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LINK_ACCOUNT_GET_HELP_BUTTON).performScrollTo().performClick()

    assert(emittedEvent == LinkAccountScreenEvent.GetHelpClick)
  }

  @Test
  fun `when Create Account is clicked, CreateAccountClick event is emitted`() {
    var emittedEvent: LinkAccountScreenEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        LinkAccountScreen(
          state = LinkAccountScreenState(),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LINK_ACCOUNT_CREATE_ACCOUNT_LINK).performClick()

    assert(emittedEvent == LinkAccountScreenEvent.CreateAccountClick)
  }

  @Test
  fun `when Display Overlay is clicked, DisplayOverlayClick event is emitted`() {
    var emittedEvent: LinkAccountScreenEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        LinkAccountScreen(
          state = LinkAccountScreenState(qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData("sgnl://test", true))),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LINK_ACCOUNT_DISPLAY_OVERLAY_BUTTON).performClick()

    assert(emittedEvent == LinkAccountScreenEvent.DisplayOverlayClick)
  }

  @Test
  fun `when Hide Overlay is clicked, HideOverlayClick event is emitted`() {
    var emittedEvent: LinkAccountScreenEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        LinkAccountScreen(
          state = LinkAccountScreenState(
            qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData("sgnl://test", true)),
            displayQrOverlay = true
          ),
          onEvent = {
            emittedEvent = it
          }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LINK_ACCOUNT_HIDE_OVERLAY_BUTTON).performClick()

    assert(emittedEvent == LinkAccountScreenEvent.HideOverlayClick)
  }
}

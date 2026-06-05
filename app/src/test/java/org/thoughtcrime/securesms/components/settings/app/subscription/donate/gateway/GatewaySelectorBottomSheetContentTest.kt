/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.donations.InAppPaymentType

/**
 * Tests for GatewaySelectorBottomSheetContent that validate event emissions.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@Ignore("Does not work with prod release builds")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GatewaySelectorBottomSheetContentTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `when Google Pay is clicked, GOOGLE_PAY_SELECTED event is emitted`() {
    // Given
    var emittedEvent: GatewaySelectorBottomSheetEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        GatewaySelectorBottomSheetContent(
          state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON))
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).performClick()

    // Then
    assertAllButtonsAreDisabled()
    assert(emittedEvent == GatewaySelectorBottomSheetEvent.GOOGLE_PAY_SELECTED)
  }

  @Test
  fun `when PayPal is clicked, PAYPAL_SELECTED event is emitted`() {
    // Given
    var emittedEvent: GatewaySelectorBottomSheetEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        GatewaySelectorBottomSheetContent(
          state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.PAYPAL_BUTTON))
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.PAYPAL_BUTTON).performClick()

    // Then
    assertAllButtonsAreDisabled()
    assert(emittedEvent == GatewaySelectorBottomSheetEvent.PAYPAL_SELECTED)
  }

  @Test
  fun `when iDEAL is clicked, IDEAL_SELECTED event is emitted`() {
    // Given
    var emittedEvent: GatewaySelectorBottomSheetEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        GatewaySelectorBottomSheetContent(
          state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.IDEAL_BUTTON))
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.IDEAL_BUTTON).performClick()

    // Then
    assertAllButtonsAreDisabled()
    assert(emittedEvent == GatewaySelectorBottomSheetEvent.IDEAL_SELECTED)
  }

  @Test
  fun `when SEPA is clicked, SEPA_SELECTED event is emitted`() {
    // Given
    var emittedEvent: GatewaySelectorBottomSheetEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        GatewaySelectorBottomSheetContent(
          state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.SEPA_BUTTON))
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.SEPA_BUTTON).performClick()

    // Then
    assertAllButtonsAreDisabled()
    assert(emittedEvent == GatewaySelectorBottomSheetEvent.SEPA_SELECTED)
  }

  @Test
  fun `when Credit Card is clicked, CREDIT_CARD_SELECTED event is emitted`() {
    // Given
    var emittedEvent: GatewaySelectorBottomSheetEvent? = null

    composeTestRule.setContent {
      SignalTheme {
        GatewaySelectorBottomSheetContent(
          state = rememberGatewaySelectorBottomSheetContentPreviewState(InAppPaymentType.ONE_TIME_GIFT),
          onEvent = { event ->
            emittedEvent = event
          }
        )
      }
    }

    // When
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CONTAINER).performScrollToNode(hasTestTag(GatewaySelectorTestTags.CREDIT_CARD_BUTTON))
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CREDIT_CARD_BUTTON).performClick()

    // Then
    assertAllButtonsAreDisabled()
    assert(emittedEvent == GatewaySelectorBottomSheetEvent.CREDIT_CARD_SELECTED)
  }

  private fun assertAllButtonsAreDisabled() {
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.GOOGLE_PAY_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.CREDIT_CARD_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.IDEAL_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.SEPA_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(GatewaySelectorTestTags.PAYPAL_BUTTON).assertIsNotEnabled()
  }
}

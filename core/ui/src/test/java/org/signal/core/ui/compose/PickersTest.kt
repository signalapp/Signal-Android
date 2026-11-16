/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class PickersTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val confirmed = mutableListOf<Duration>()

  @Test
  fun `the initial duration is split into the largest unit that can hold it`() {
    setContent(initialDuration = 3.hours)

    confirm()

    assertThat(confirmed).containsExactly(3.hours)
  }

  @Test
  fun `a duration too long for any unit uses the largest unit offered`() {
    setContent(initialDuration = 30.days, units = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS))

    confirm()

    assertThat(confirmed).containsExactly(6.days)
  }

  @Test
  fun `no initial duration starts at the smallest value of the first unit`() {
    setContent(initialDuration = Duration.ZERO, units = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS))

    confirm()

    assertThat(confirmed).containsExactly(1.minutes)
  }

  @Test
  fun `picking a value and a unit confirms their product`() {
    setContent(initialDuration = 1.minutes, units = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS))

    pick(Pickers.TEST_TAG_DURATION_VALUE_WHEEL, "4")
    pick(Pickers.TEST_TAG_DURATION_UNIT_WHEEL, "days")
    confirm()

    assertThat(confirmed).containsExactly(4.days)
  }

  @Test
  fun `switching to a smaller unit clamps the value into its range`() {
    setContent(initialDuration = 45.minutes, units = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS))

    pick(Pickers.TEST_TAG_DURATION_UNIT_WHEEL, "days")
    confirm()

    assertThat(confirmed).containsExactly(6.days)
  }

  private fun setContent(initialDuration: Duration, units: List<DurationPickerUnit> = DurationPickerUnit.entries) {
    composeTestRule.setContent {
      Pickers.DurationDialog(
        title = "Custom time",
        initialDuration = initialDuration,
        units = units,
        onConfirm = { confirmed += it },
        onDismiss = {}
      )
    }
  }

  private fun pick(wheelTag: String, text: String) {
    val item = hasText(text) and hasAnyAncestor(hasTestTag(wheelTag))
    composeTestRule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(wheelTag))).performScrollToNode(item)
    composeTestRule.onNode(item).performClick()
    composeTestRule.waitForIdle()
  }

  private fun confirm() {
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R
import org.signal.mediasend.test.TestTags

/**
 * Covers what the schedule menu puts in front of the user: a row per suggested time labelled by the host app's date
 * formatter, and the option each row reports back when tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScheduleSendMenuTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val context: Application = ApplicationProvider.getApplicationContext()

  private var clickedOption: ScheduleSendOption? = null

  @Test
  fun `Given suggested times, when displayed, then each is labelled by the injected formatter`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.scheduleSendPresetOption(TONIGHT)).assertTextEquals("Scheduled for $TONIGHT")
    composeTestRule.onNodeWithTag(TestTags.scheduleSendPresetOption(TOMORROW)).assertTextEquals("Scheduled for $TOMORROW")
  }

  @Test
  fun `Given a pick a time option, when displayed, then it is labelled from the module's strings`() {
    setContent()

    composeTestRule
      .onNodeWithTag(TestTags.SCHEDULE_SEND_PICK_TIME_OPTION)
      .assertTextEquals(context.getString(R.string.ScheduleSendMenu__pick_date_and_time))
  }

  @Test
  fun `Given suggested times, when one is clicked, then that time is reported`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.scheduleSendPresetOption(TOMORROW)).performClick()

    assertEquals(ScheduleSendOption.PresetTime(TOMORROW), clickedOption)
  }

  @Test
  fun `Given a pick a time option, when it is clicked, then picking a time is reported`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.SCHEDULE_SEND_PICK_TIME_OPTION).performClick()

    assertEquals(ScheduleSendOption.PickTime, clickedOption)
  }

  @Test
  fun `Given an evening and a morning suggestion, when displayed, then both are shown`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.scheduleSendPresetOption(TONIGHT)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.scheduleSendPresetOption(TOMORROW)).assertIsDisplayed()
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        CompositionLocalProvider(LocalScheduledSendTimeFormatter provides { "Scheduled for $it" }) {
          Column {
            ScheduleSendOptions(
              options = listOf(
                ScheduleSendOption.PickTime,
                ScheduleSendOption.PresetTime(TOMORROW),
                ScheduleSendOption.PresetTime(TONIGHT)
              ),
              onOptionClick = { clickedOption = it }
            )
          }
        }
      }
    }
  }

  companion object {
    /** 2026-07-05 21:00 UTC, an hour that renders with the night icon. */
    private const val TONIGHT = 1783285200000L

    /** 2026-07-06 08:00 UTC, an hour that renders with the day icon. */
    private const val TOMORROW = 1783324800000L
  }
}

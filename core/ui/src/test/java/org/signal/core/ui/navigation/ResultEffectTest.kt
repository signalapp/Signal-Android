/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.navigation

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResultEffectTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val bus = ResultEventBus()
  private val received = mutableListOf<String>()

  @Test
  fun `a result sent after the receiver is composed is delivered`() {
    setContent()

    bus.sendResult(result = "first")
    composeTestRule.waitForIdle()

    assertThat(received).containsExactly("first")
  }

  @Test
  fun `a result sent before the receiver is composed is delivered`() {
    bus.sendResult(result = "first")

    setContent()

    assertThat(received).containsExactly("first")
  }

  @Test
  fun `results of another type are not delivered`() {
    setContent()

    bus.sendResult(result = 42)
    composeTestRule.waitForIdle()

    assertThat(received).isEmpty()
  }

  private fun setContent() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalResultEventBus provides bus) {
        ResultEffect<String> { received += it }
      }
    }
    composeTestRule.waitForIdle()
  }
}

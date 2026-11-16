/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChipsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `tapping a removable chip removes it`() {
    var removed = 0
    composeTestRule.setContent {
      Chips.Removable(label = "Miles", onRemove = { removed++ })
    }

    composeTestRule.onNodeWithText("Miles").performClick()

    assertThat(removed).isEqualTo(1)
  }

  @Test
  fun `a removable chip tells accessibility services that tapping removes it`() {
    var removed = 0
    composeTestRule.setContent {
      Chips.Removable(label = "Miles", onRemove = { removed++ })
    }

    composeTestRule.onNode(SemanticsMatcher("click is labeled Remove Miles") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Remove Miles" })
      .performSemanticsAction(SemanticsActions.OnClick)

    assertThat(removed).isEqualTo(1)
  }
}

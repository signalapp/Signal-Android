/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks that [BottomSheets.BottomSheet] tells its content where the sheet is, and lets it close the sheet, as
 * [ComposeBottomSheetDialogFragment] does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BottomSheetsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `content sees the sheet as expanded once it is`() {
    composeTestRule.setContent {
      BottomSheets.BottomSheet(
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ) {
        Text(text = "expanded=${LocalBottomSheetHostState.current.isExpanded}", modifier = Modifier.height(100.dp))
      }
    }

    composeTestRule.onNodeWithText("expanded=true").assertExists()
  }

  @Test
  fun `a partly open sheet reports how far its content hangs below the window`() {
    composeTestRule.setContent {
      BottomSheets.BottomSheet(onDismissRequest = {}) {
        val overflow = LocalBottomSheetHostState.current.overflowBottomPx
        Text(text = if (overflow > 0) "overflowing" else "fits", modifier = Modifier.fillMaxSize())
      }
    }

    composeTestRule.onNodeWithText("overflowing").assertExists()
  }

  @Test
  fun `dismissing from the content hides the sheet before asking its host to close it`() {
    var dismissRequests = 0
    var sheetVisibleWhenDismissed: Boolean? = null

    composeTestRule.setContent {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      BottomSheets.BottomSheet(
        onDismissRequest = {
          dismissRequests++
          sheetVisibleWhenDismissed = sheetState.isVisible
        },
        sheetState = sheetState
      ) {
        val sheet = LocalBottomSheetHostState.current
        Text(text = "close", modifier = Modifier.height(100.dp).clickable { sheet.dismiss() })
      }
    }

    composeTestRule.onNodeWithText("close").performClick()
    composeTestRule.waitForIdle()

    assertThat(dismissRequests).isEqualTo(1)
    assertThat(sheetVisibleWhenDismissed).isEqualTo(false)
  }
}

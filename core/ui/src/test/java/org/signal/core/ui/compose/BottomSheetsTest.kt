/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.core.ui.compose

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checks that [BottomSheets.BottomSheet] tells its content where the sheet is, as [ComposeBottomSheetDialogFragment] does.
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
}

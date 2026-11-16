/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w1000dp-h800dp")
class DetailPaneStackSwapTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(TestListKey.ROOT, TestDetailKey(1))
  private val deferredEnters = mutableMapOf<Int, DeferredEnter>()

  @Test
  fun `swapping to a deferred entry keeps the outgoing entry until the new one starts`() {
    setContent()

    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(2)) }

    composeTestRule.onNodeWithText("detail 1").assertExists()
    composeTestRule.onNodeWithText("detail 2").assertExists()

    composeTestRule.runOnIdle { deferredEnters.getValue(2).start() }

    composeTestRule.onNodeWithText("detail 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("detail 2").assertExists()
  }

  @Test
  fun `the outgoing entry is dropped once the deferred enter times out`() {
    setContent()
    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(2)) }
    composeTestRule.mainClock.advanceTimeByFrame()
    composeTestRule.onNodeWithText("detail 1").assertExists()

    composeTestRule.mainClock.advanceTimeBy(DEFERRED_ENTER_TIMEOUT.inWholeMilliseconds + 100)

    composeTestRule.onNodeWithText("detail 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("detail 2").assertExists()
  }

  @Test
  fun `a second swap while one is pending keeps the entry that was on screen`() {
    setContent()

    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(2)) }
    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(3)) }

    composeTestRule.onNodeWithText("detail 1").assertExists()
    composeTestRule.onNodeWithText("detail 2").assertDoesNotExist()

    composeTestRule.runOnIdle { deferredEnters.getValue(3).start() }

    composeTestRule.onNodeWithText("detail 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("detail 3").assertExists()
  }

  @Test
  fun `swapping back to the outgoing entry while a swap is pending shows it on its own`() {
    setContent()

    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(2)) }
    composeTestRule.runOnIdle { backStack.swapDetailTo(TestDetailKey(1)) }

    composeTestRule.onNodeWithText("detail 1").assertExists()
    composeTestRule.onNodeWithText("detail 2").assertDoesNotExist()
  }

  @Test
  fun `a swap made beside a different list is not deferred`() {
    setContent()

    composeTestRule.runOnIdle {
      backStack.clear()
      backStack += listOf(TestListKey.OTHER, TestDetailKey(2))
    }

    composeTestRule.onNodeWithText("detail 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("detail 2").assertExists()
  }

  @Test
  fun `pushing within the detail stack is not deferred`() {
    setContent()

    composeTestRule.runOnIdle { backStack += TestSubScreenKey(2) }

    composeTestRule.onNodeWithText("detail 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("sub 2").assertExists()
  }

  private fun MutableList<NavKey>.swapDetailTo(key: TestDetailKey) {
    removeAt(lastIndex)
    add(key)
  }

  private fun setContent() {
    composeTestRule.setContent {
      val layout = rememberListDetailPaneLayout(paneAnchor = PaneAnchor.SPLIT, maxWidth = 1000.dp, onAnchorSelected = {})
      val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider {
          listEntry<TestListKey> { Text("list") }
          detailEntry<TestDetailKey>(metadata = deferredEnterMetadata()) {
            deferredEnters[it.id] = LocalDeferredEnter.current
            Text("detail ${it.id}")
          }
          detailEntry<TestSubScreenKey> { Text("sub ${it.id}") }
        }
      )

      ListDetailNavDisplay(
        entries = entries,
        isSplitPane = true,
        paneAnchor = PaneAnchor.SPLIT,
        onBack = {},
        onExitDetail = {},
        layout = layout
      )
    }
  }
}

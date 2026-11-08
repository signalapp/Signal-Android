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
class DetailPaneRootTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(TestListKey.ROOT, TestDetailKey(1))

  @Test
  fun `the only entry in the detail stack is its root`() {
    setContent(isSplitPane = true)

    composeTestRule.onNodeWithText("detail 1 root=true").assertExists()
  }

  @Test
  fun `an entry pushed onto the detail stack is not its root`() {
    setContent(isSplitPane = true)

    composeTestRule.runOnIdle { backStack += TestSubScreenKey(2) }

    composeTestRule.onNodeWithText("sub 2 root=false").assertExists()
  }

  @Test
  fun `popping back to the bottom of the detail stack makes it the root again`() {
    backStack += TestSubScreenKey(2)
    setContent(isSplitPane = true)

    composeTestRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }

    composeTestRule.onNodeWithText("detail 1 root=true").assertExists()
  }

  @Test
  fun `a detail entry filling a single pane is not a detail pane root`() {
    setContent(isSplitPane = false)

    composeTestRule.onNodeWithText("detail 1 root=false").assertExists()
  }

  private fun setContent(isSplitPane: Boolean) {
    composeTestRule.setContent {
      val layout = rememberListDetailPaneLayout(paneAnchor = PaneAnchor.SPLIT, maxWidth = 1000.dp, onAnchorSelected = {})
      val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider {
          listEntry<TestListKey> { Text("list") }
          detailEntry<TestDetailKey> { Text("detail ${it.id} root=${LocalIsDetailPaneRoot.current}") }
          detailEntry<TestSubScreenKey> { Text("sub ${it.id} root=${LocalIsDetailPaneRoot.current}") }
        }
      )

      ListDetailNavDisplay(
        entries = entries,
        isSplitPane = isSplitPane,
        paneAnchor = PaneAnchor.SPLIT,
        onBack = {},
        onExitDetail = {},
        layout = layout
      )
    }
  }
}

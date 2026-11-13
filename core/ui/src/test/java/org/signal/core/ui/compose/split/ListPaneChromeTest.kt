/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class ListPaneChromeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>()
  private val chromeByList = mutableMapOf<TestListKey, MutableList<Int>>()
  private var nextChromeId = 0

  @Test
  fun `chrome state follows the list across a tab switch`() {
    setContent(TestListKey.ROOT)
    val original = chromeOf(TestListKey.ROOT)

    update { backStack.clear(); backStack += TestListKey.OTHER }
    composeTestRule.mainClock.advanceTimeBy(1000)

    assertEquals(original, chromeOf(TestListKey.OTHER))
  }

  @Test
  fun `chrome state goes to the incoming list while a different list animates out`() {
    setContent(TestListKey.ROOT, TestListKey.PUSHED)
    val original = chromeOf(TestListKey.PUSHED)

    update { backStack += TestDetailKey(1) }
    composeTestRule.mainClock.advanceTimeBy(350)

    update { backStack.clear(); backStack += TestListKey.ROOT }
    repeat(2) { composeTestRule.mainClock.advanceTimeByFrame() }

    assertEquals(1, chromeByList.getValue(TestListKey.PUSHED).size)
    assertEquals(original, chromeOf(TestListKey.ROOT))

    composeTestRule.mainClock.advanceTimeBy(1000)

    assertEquals(emptyList<Int>(), chromeByList.getValue(TestListKey.PUSHED))
    assertEquals(original, chromeOf(TestListKey.ROOT))
  }

  private fun chromeOf(key: TestListKey): Int {
    return chromeByList.getValue(key).single()
  }

  private fun update(block: () -> Unit) {
    composeTestRule.runOnUiThread {
      block()
      Snapshot.sendApplyNotifications()
    }
  }

  private fun setContent(vararg initial: NavKey) {
    backStack += initial
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
      val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider {
          listEntry<TestListKey> { key ->
            val chromeId = LocalChromeId.current
            DisposableEffect(key, chromeId) {
              chromeByList.getOrPut(key) { mutableListOf() } += chromeId
              onDispose { chromeByList.getValue(key) -= chromeId }
            }
            Text("list $key")
          }
          detailEntry<TestDetailKey>(metadata = deferredEnterMetadata()) { Text("detail ${it.id}") }
        }
      )

      ListDetailNavDisplay(
        entries = entries,
        isSplitPane = false,
        paneAnchor = PaneAnchor.SPLIT,
        onBack = {},
        onExitDetail = {},
        listPaneChrome = { content ->
          val chromeId = remember { nextChromeId++ }
          CompositionLocalProvider(LocalChromeId provides chromeId) {
            Column {
              Text("toolbar")
              content()
            }
          }
        }
      )
    }
    composeTestRule.mainClock.advanceTimeBy(1000)
  }
}

private val LocalChromeId = compositionLocalOf { -1 }

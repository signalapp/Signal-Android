/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what a scene reports as its previous entries, because that is the whole of the display's back
 * behaviour: `NavDisplay` enables its back handler when a scene has previous entries, and pops the
 * difference between the stack and them. Both are reproduced here by [isBackEnabled] and [popsOnBack].
 */
class ListDetailSceneStrategyTest {

  private val rootEntry = listEntry(TestListKey.ROOT)

  private val pushedEntry = listEntry(TestListKey.PUSHED)

  private val otherRootEntry = listEntry(TestListKey.OTHER)

  private val detailEntry = detailEntry(TestDetailKey(1))
  private val subScreenEntry = detailEntry(TestSubScreenKey(1))

  private val fullScreenEntry = plainEntry(TestFullScreenKey)

  @Test
  fun `given split pane showing a list on its own, then back is left to the rest of the app`() {
    val entries = listOf(rootEntry)

    assertFalse(splitPane(entries).isBackEnabled)
  }

  /**
   * The empty detail pane is not a destination: closing the last piece of detail content would leave the
   * user on the same list looking at a placeholder, so back is left to the handlers outside the display.
   */
  @Test
  fun `given split pane showing a list and one detail, then back is left to the rest of the app`() {
    val entries = listOf(rootEntry, detailEntry)

    assertFalse(splitPane(entries).isBackEnabled)
  }

  @Test
  fun `given split pane showing stacked detail, when going back, then only the top detail is popped`() {
    val entries = listOf(rootEntry, detailEntry, subScreenEntry)
    val scene = splitPane(entries)

    assertTrue(scene.isBackEnabled)
    assertEquals(1, scene.popsOnBack(entries))
    assertEquals(listOf(rootEntry, detailEntry), scene.previousEntries)
  }

  @Test
  fun `given split pane showing a pushed list, when going back, then that list is popped`() {
    val entries = listOf(rootEntry, pushedEntry)
    val scene = splitPane(entries)

    assertTrue(scene.isBackEnabled)
    assertEquals(1, scene.popsOnBack(entries))
    assertEquals(listOf(rootEntry), scene.previousEntries)
  }

  /**
   * Backing out of a pushed list with detail open takes the detail with it, rather than stopping on the
   * pushed list with an empty detail pane.
   */
  @Test
  fun `given split pane showing detail over a pushed list, when going back, then both are popped`() {
    val entries = listOf(rootEntry, pushedEntry, detailEntry)
    val scene = splitPane(entries)

    assertTrue(scene.isBackEnabled)
    assertEquals(2, scene.popsOnBack(entries))
    assertEquals(listOf(rootEntry), scene.previousEntries)
  }

  @Test
  fun `given split pane, then it displays the current list beside the topmost detail`() {
    val scene = splitPane(listOf(rootEntry, pushedEntry, detailEntry, subScreenEntry))

    assertEquals(listOf(pushedEntry, subScreenEntry), scene.entries)
  }

  /**
   * One scene identity for every list, so that swapping between them is not a scene change and the
   * display does not cross-fade the whole window.
   */
  @Test
  fun `given split pane, then every list shares one scene identity`() {
    val root = splitPane(listOf(rootEntry))
    val other = splitPane(listOf(otherRootEntry))

    assertEquals(root.key, other.key)
  }

  @Test
  fun `given a single pane showing a list on its own, then back is left to the rest of the app`() {
    assertFalse(singlePane(listOf(rootEntry)).isBackEnabled)
  }

  /**
   * The single-pane counterpart of the split-pane case above: here the detail covers the list rather than
   * sitting beside it, so closing it is a real destination.
   */
  @Test
  fun `given a single pane showing detail, when going back, then the detail is popped`() {
    val entries = listOf(rootEntry, detailEntry)
    val scene = singlePane(entries)

    assertTrue(scene.isBackEnabled)
    assertEquals(1, scene.popsOnBack(entries))
  }

  @Test
  fun `given a single pane showing detail over a pushed list, when going back, then the list stays`() {
    val entries = listOf(rootEntry, pushedEntry, detailEntry)
    val scene = singlePane(entries)

    assertEquals(1, scene.popsOnBack(entries))
    assertEquals(listOf(rootEntry, pushedEntry), scene.previousEntries)
  }

  @Test
  fun `given a single pane, then it displays only the top entry`() {
    val scene = singlePane(listOf(rootEntry, detailEntry))

    assertEquals(listOf(detailEntry), scene.entries)
  }

  @Test
  fun `given a single pane, then lists share an identity that detail content does not`() {
    val root = singlePane(listOf(rootEntry))
    val other = singlePane(listOf(otherRootEntry))
    val detail = singlePane(listOf(rootEntry, detailEntry))

    assertEquals(root.key, other.key)
    assertNotEquals(root.key, detail.key)
  }

  @Test
  fun `given split pane with a full screen entry on top, then it is displayed instead of the panes`() {
    val scene = splitPane(listOf(rootEntry, detailEntry, fullScreenEntry))

    assertEquals(listOf(fullScreenEntry), scene.entries)
  }

  @Test
  fun `given a full screen entry on top, when going back, then only it is popped`() {
    val entries = listOf(rootEntry, detailEntry, fullScreenEntry)

    listOf(splitPane(entries), singlePane(entries)).forEach { scene ->
      assertTrue(scene.isBackEnabled)
      assertEquals(1, scene.popsOnBack(entries))
      assertEquals(listOf(rootEntry, detailEntry), scene.previousEntries)
    }
  }

  /**
   * The list keeps one scene identity so that swapping lists does not cross-fade the window; a full screen
   * entry is a window of its own, and moving to it should be a scene change.
   */
  @Test
  fun `given a full screen entry, then it does not share the list scene identity`() {
    val list = splitPane(listOf(rootEntry))
    val fullScreen = splitPane(listOf(rootEntry, fullScreenEntry))

    assertNotEquals(list.key, fullScreen.key)
  }

  private fun splitPane(entries: List<NavEntry<NavKey>>): Scene<NavKey> = scene(entries, isSplitPane = true)

  private fun singlePane(entries: List<NavEntry<NavKey>>): Scene<NavKey> = scene(entries, isSplitPane = false)

  private fun scene(entries: List<NavEntry<NavKey>>, isSplitPane: Boolean): Scene<NavKey> {
    return with(ListDetailSceneStrategy(isSplitPane)) {
      SceneStrategyScope<NavKey>().calculateScene(entries)
    }!!
  }

  /** What `NavDisplay` uses to decide whether to register a back handler at all. */
  private val Scene<NavKey>.isBackEnabled: Boolean
    get() = previousEntries.isNotEmpty()

  /** What `NavDisplay` pops when its back handler completes. */
  private fun Scene<NavKey>.popsOnBack(entries: List<NavEntry<NavKey>>): Int = entries.size - previousEntries.size

  private fun listEntry(location: ListNavKey): NavEntry<NavKey> {
    return NavEntry(key = location, metadata = listPaneMetadata()) {}
  }

  private fun detailEntry(location: DetailNavKey): NavEntry<NavKey> {
    return NavEntry(key = location, metadata = detailPaneMetadata()) {}
  }

  /** An entry registered with a plain `entry`, claiming neither pane. */
  private fun plainEntry(location: NavKey): NavEntry<NavKey> {
    return NavEntry(key = location) {}
  }
}

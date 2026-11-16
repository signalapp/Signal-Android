/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.list

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Laid out like the media-send thumbnail rail: first visible item centered, earlier items in the start padding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class DragToReorderTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val items = (0 until 12).toList().toMutableStateList()
  private val moves = mutableListOf<Pair<Int, Int>>()

  @Test
  fun `Given the first visible item is dragged forward past its neighbour, when the list has laid out again and the drag goes on, then it has only moved one slot`() {
    setContent(firstVisibleItemIndex = 0)

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      down(itemCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(itemCenter(1) + Offset(overshootPx, 0f))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      moveBy(Offset(1f, 0f))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(moves).isEqualTo(listOf(0 to 1))
    assertThat(items.take(4)).isEqualTo(listOf(1, 0, 2, 3))
  }

  @Test
  fun `Given the first visible item is dragged backward past its neighbour, when the list has laid out again and the drag goes on, then it has only moved one slot`() {
    setContent(firstVisibleItemIndex = 2)

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      down(itemCenter(2))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(itemCenter(1) - Offset(overshootPx, 0f))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      moveBy(Offset(-1f, 0f))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(LIST).performTouchInput {
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(moves).isEqualTo(listOf(2 to 1))
    assertThat(items.take(4)).isEqualTo(listOf(0, 2, 1, 3))
  }

  private fun itemCenter(item: Int): Offset {
    val node = composeTestRule.onNodeWithTag(itemTag(item)).fetchSemanticsNode()

    return Offset(
      node.positionInRoot.x + node.size.width / 2f,
      node.positionInRoot.y + node.size.height / 2f
    )
  }

  private val overshootPx: Float
    get() = with(composeTestRule.density) { OVERSHOOT.toPx() }

  private fun setContent(firstVisibleItemIndex: Int) {
    composeTestRule.setContent {
      val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstVisibleItemIndex)
      val reorderableListState = rememberReorderableListState(
        lazyListState = listState,
        includeHeader = false,
        includeFooter = false,
        orientation = Orientation.Horizontal,
        autoScroll = false,
        onEvent = { event ->
          if (event is ReorderListEvent.ItemMoved) {
            moves += event.fromIndex to event.toIndex
            items.add(event.toIndex, items.removeAt(event.fromIndex))
          }
        }
      )

      Box(modifier = Modifier.size(LIST_WIDTH, LIST_HEIGHT)) {
        LazyRow(
          state = listState,
          userScrollEnabled = false,
          horizontalArrangement = Arrangement.spacedBy(SPACING),
          contentPadding = PaddingValues(start = (LIST_WIDTH - ITEM_SIZE) / 2),
          modifier = Modifier
            .testTag(LIST)
            .reorderableList(reorderableListState)
        ) {
          itemsIndexed(items, key = { _, item -> item }) { index, item ->
            ReorderableItem(reorderableListState = reorderableListState, index = index) {
              Box(
                modifier = Modifier
                  .size(ITEM_SIZE)
                  .background(Color.Gray)
                  .testTag(itemTag(item))
              )
            }
          }
        }
      }
    }

    composeTestRule.waitForIdle()
  }

  private fun itemTag(item: Int): String = "item_$item"

  private companion object {
    private const val LIST = "list"
    private const val LONG_PRESS_MS = 600L

    private val LIST_WIDTH = 400.dp
    private val LIST_HEIGHT = 100.dp
    private val ITEM_SIZE = 48.dp
    private val SPACING = 4.dp
    private val OVERSHOOT = 10.dp
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import org.signal.core.ui.R
import kotlin.math.max

/**
 * Renders a list entry and, when there is one, the detail entry above it side by side.
 *
 * There is some custom equality code in here to make sure animations are correct.
 */
@Immutable
internal class ListDetailScene(
  override val key: Any,
  override val previousEntries: List<NavEntry<NavKey>>,
  private val listEntry: NavEntry<NavKey>,
  private val detailEntry: NavEntry<NavKey>?
) : Scene<NavKey> {

  override val entries: List<NavEntry<NavKey>> = listOfNotNull(listEntry, detailEntry)

  override val content: @Composable () -> Unit = {
    val layout = LocalListDetailPaneLayout.current

    if (layout == null) {
      listEntry.Content()
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .width(layout.expansion.listWidth)
              .fillMaxHeight()
              .padding(start = layout.listPaddingStart)
              .clip(layout.shape)
              .minContentWidth(layout.minPaneContentWidth)
          ) {
            ListPaneContent { listEntry.Content() }
          }

          Spacer(modifier = Modifier.width(layout.partitionWidth))

          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .padding(end = layout.detailPaddingEnd)
              .clip(layout.shape)
              .background(color = MaterialTheme.colorScheme.surface)
              .minContentWidth(layout.minPaneContentWidth)
          ) {
            if (detailEntry != null) {
              detailEntry.Content()
            } else {
              LocalEmptyDetailContent.current()
            }
          }
        }

        if (layout.hasDragHandle) {
          PaneDragHandle(layout = layout)
        }
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ListDetailScene) return false

    return key == other.key &&
      listEntry == other.listEntry &&
      detailEntry == other.detailEntry &&
      previousEntries == other.previousEntries
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + listEntry.hashCode()
    result = 31 * result + (detailEntry?.hashCode() ?: 0)
    result = 31 * result + previousEntries.hashCode()
    return result
  }
}

/**
 * Pane divider modeled after the one from the material libraries that allows the user to change the size of the list or detail pane.
 */
@Composable
private fun BoxScope.PaneDragHandle(layout: ListDetailPaneLayout) {
  val interactionSource = remember { MutableInteractionSource() }
  val density = LocalDensity.current

  val dividerDescription = stringResource(R.string.ListDetailPane__accessibility_pane_divider)
  val moveLabel = stringResource(R.string.ListDetailPane__accessibility_move_pane_divider)

  val draggableState = rememberDraggableState { delta -> layout.expansion.dragBy(delta) }

  val splitCenter = layout.expansion.listWidth + (layout.partitionWidth / 2)
  val touchStart = splitCenter - (PANE_HANDLE_TOUCH_WIDTH / 2)

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .align(Alignment.CenterStart)
      .offset { IntOffset(with(density) { touchStart.roundToPx() }, 0) }
      .size(PANE_HANDLE_TOUCH_WIDTH, PANE_HANDLE_TOUCH_HEIGHT)
      .draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        interactionSource = interactionSource,
        onDragStarted = { layout.expansion.onDragStarted() },
        onDragStopped = { layout.expansion.settle() }
      )
      .semantics {
        contentDescription = dividerDescription
        onClick(label = moveLabel) {
          val next = layout.expansion.nextAnchor()

          if (next != null) {
            layout.expansion.selectAnchor(next)
            true
          } else {
            false
          }
        }
      }
      .systemGestureExclusion()
  ) {
    Box(
      modifier = Modifier
        .size(PANE_HANDLE_VISUAL_WIDTH, PANE_HANDLE_VISUAL_HEIGHT)
        .background(color = Color(0xFF605F5D), RoundedCornerShape(percent = 50))
    )
  }
}

/**
 * Measures content at no less than [minWidth] while still reporting the slot's actual width. This makes sure that as the user
 * makes a pane smaller and smaller the UI doesn't distort and look weird.
 */
private fun Modifier.minContentWidth(minWidth: Dp): Modifier {
  return layout { measurable, constraints ->
    val min = minWidth.roundToPx()
    val placeable = measurable.measure(
      constraints.copy(
        minWidth = min,
        maxWidth = max(min, constraints.maxWidth)
      )
    )

    layout(constraints.maxWidth, placeable.height) {
      placeable.placeRelative(x = 0, y = 0)
    }
  }
}

/**
 * Renders [content] wrapped in the list pane chrome, if any has been provided.
 */
@Composable
private fun ListPaneContent(content: @Composable () -> Unit) {
  val chrome = LocalListPaneChrome.current

  if (chrome != null) {
    chrome(content)
  } else {
    content()
  }
}

/**
 * Single-pane counterpart to [ListDetailScene]. We roll our own because otherwise animations get weird when swapping
 * between lists.
 */
@Immutable
internal class SinglePaneScene(
  override val key: Any,
  override val previousEntries: List<NavEntry<NavKey>>,
  private val entry: NavEntry<NavKey>,
  private val isList: Boolean
) : Scene<NavKey> {

  override val entries: List<NavEntry<NavKey>> = listOf(entry)

  override val content: @Composable () -> Unit = {
    if (isList) {
      ListPaneContent { entry.Content() }
    } else {
      entry.Content()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SinglePaneScene) return false

    return key == other.key &&
      entry == other.entry &&
      isList == other.isList &&
      previousEntries == other.previousEntries
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + entry.hashCode()
    result = 31 * result + isList.hashCode()
    result = 31 * result + previousEntries.hashCode()
    return result
  }
}

/**
 * Renders the given backstack depending on what kind of screen we're on. Note that we do utilize fixed keys here because we want to make
 * sure that as we move between screens in either pane we are *only* animating that panes content and not the whole scene.
 */
internal class ListDetailSceneStrategy(private val isSplitPane: Boolean) : SceneStrategy<NavKey> {

  override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
    val top = entries.lastOrNull() ?: return null

    if (top.isFullScreen) {
      return SinglePaneScene(
        key = top.contentKey,
        previousEntries = entries.dropLast(1),
        entry = top,
        isList = false
      )
    }

    if (!isSplitPane) {
      val isList = top.isListPane

      return SinglePaneScene(
        key = if (isList) LIST_SCENE_KEY else top.contentKey,
        previousEntries = entries.dropLast(1),
        entry = top,
        isList = isList
      )
    }

    val listIndex = entries.indexOfLast { it.isListPane }
    if (listIndex < 0) {
      return null
    }

    val detailEntries = entries.subList(listIndex + 1, entries.size)

    return ListDetailScene(
      key = DETAIL_SCENE_KEY,
      previousEntries = if (detailEntries.size > 1) entries.dropLast(1) else entries.take(listIndex),
      listEntry = entries[listIndex],
      detailEntry = detailEntries.lastOrNull()
    )
  }
}

/** Fixed detail key */
private const val DETAIL_SCENE_KEY = "signal.listDetail"

/** Fixed list key */
private const val LIST_SCENE_KEY = "signal.list"

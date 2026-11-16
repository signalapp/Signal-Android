/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class InlineVideoPlaybackPlanTest {

  private data class Cell(val name: String, val center: Float)

  private data class Slot(val name: String, val mediaItem: String?)

  @Test
  fun `selectNearestCenter returns the closest cells to the viewport center, nearest first`() {
    val cells = listOf(
      Cell("top", 50f),
      Cell("upper", 400f),
      Cell("middle", 510f),
      Cell("lower", 700f),
      Cell("bottom", 990f)
    )

    val selected = InlineVideoPlaybackPlan.selectNearestCenter(cells, viewportCenter = 500f, maxSlots = 3) { it.center }

    assertThat(selected.map { it.name }).isEqualTo(listOf("middle", "upper", "lower"))
  }

  @Test
  fun `selectNearestCenter ranks by position, not by list order`() {
    val cells = listOf(
      Cell("first in adapter, far", 0f),
      Cell("second in adapter, near", 480f)
    )

    val selected = InlineVideoPlaybackPlan.selectNearestCenter(cells, viewportCenter = 500f, maxSlots = 1) { it.center }

    assertThat(selected.map { it.name }).isEqualTo(listOf("second in adapter, near"))
  }

  @Test
  fun `selectNearestCenter returns everything when there are fewer cells than slots`() {
    val cells = listOf(Cell("a", 100f), Cell("b", 900f))

    val selected = InlineVideoPlaybackPlan.selectNearestCenter(cells, viewportCenter = 500f, maxSlots = 5) { it.center }

    assertThat(selected.map { it.name }.toSet()).isEqualTo(setOf("a", "b"))
  }

  @Test
  fun `selectNearestCenter returns nothing when there are no slots`() {
    val cells = listOf(Cell("a", 500f))

    val selected = InlineVideoPlaybackPlan.selectNearestCenter(cells, viewportCenter = 500f, maxSlots = 0) { it.center }

    assertThat(selected).isEmpty()
  }

  @Test
  fun `pickIdleSlot prefers a slot that already has the media prepared`() {
    val idle = listOf(Slot("oldest", "gif-a"), Slot("matching", "gif-b"), Slot("newest", "gif-c"))

    val picked = InlineVideoPlaybackPlan.pickIdleSlot(idle, "gif-b") { it.mediaItem }

    assertThat(picked?.name).isEqualTo("matching")
  }

  @Test
  fun `pickIdleSlot falls back to the least recently used slot`() {
    val idle = listOf(Slot("oldest", "gif-a"), Slot("newest", null))

    val picked = InlineVideoPlaybackPlan.pickIdleSlot(idle, "gif-z") { it.mediaItem }

    assertThat(picked?.name).isEqualTo("oldest")
  }

  @Test
  fun `pickIdleSlot returns null when nothing is idle`() {
    val picked = InlineVideoPlaybackPlan.pickIdleSlot(emptyList<Slot>(), "gif-a") { it.mediaItem }

    assertThat(picked).isNull()
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class ListDetailPaneMetricsTest {

  private val metrics = ListDetailPaneMetrics(
    shape = RoundedCornerShape(0.dp),
    navigationBarShape = RoundedCornerShape(0.dp),
    partitionWidth = 13.dp,
    listPaddingStart = 0.dp,
    detailPaddingEnd = 12.dp
  )

  @Test
  fun `on an open foldable the detail pane is narrower than the fixed-width list`() {
    assertThat(metrics.detailWidthAtSplit(maxWidth = 841.dp, splitListWidth = 416.dp)).isEqualTo(400.dp)
  }

  @Test
  fun `on a wide window the detail pane takes what the list and padding leave`() {
    assertThat(metrics.detailWidthAtSplit(maxWidth = 1280.dp, splitListWidth = 416.dp)).isEqualTo(839.dp)
  }

  @Test
  fun `the detail pane is never given a negative width`() {
    assertThat(metrics.detailWidthAtSplit(maxWidth = 400.dp, splitListWidth = 416.dp)).isEqualTo(0.dp)
  }

  @Test
  fun `the list pane's content is narrower than the list by its start padding`() {
    val searching = metrics.copy(listPaddingStart = 24.dp)

    assertThat(searching.listContentWidthAtSplit(splitListWidth = 416.dp)).isEqualTo(392.dp)
  }

  @Test
  fun `without start padding the list pane's content fills the list`() {
    assertThat(metrics.listContentWidthAtSplit(splitListWidth = 416.dp)).isEqualTo(416.dp)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import org.junit.Test

class StressMatrixTest {

  private val matrix = StressMatrix()

  @Test
  fun `exhaustive is every combination`() {
    assertThat(matrix.exhaustive()).hasSize(3 * 2 * 2 * 2 * 3)
  }

  @Test
  fun `pairwise covers every pair of values from two dimensions`() {
    val pairwise = matrix.pairwise()

    assertThat(pairs(matrix.exhaustive()) - pairs(pairwise)).isEmpty()
    assertThat(pairwise.size).isLessThan(matrix.exhaustive().size / 4)
  }

  @Test
  fun `pairwise starts with the everyday default and its largest font twin`() {
    assertThat(matrix.pairwise().take(2).map { it.name }).containsExactly("phone", "phone + font 2x")
  }

  @Test
  fun `display size shrinks the device window to whole dp`() {
    val config = matrix.exhaustive().first { it.name == "phone + display 1.3x" }

    assertThat(config.windowSize).isEqualTo(DpSize(277.dp, 600.dp))
  }

  @Test
  fun `names list every value that is not the everyday default`() {
    val config = matrix.exhaustive().last()

    assertThat(config.name).isEqualTo("tablet + display 1.3x + font 2x + RTL + pseudo-compound")
    assertThat(config.layoutDirection).isEqualTo(LayoutDirection.Rtl)
  }

  @Test
  fun `every window fits the robolectric qualifiers`() {
    val tooBig = matrix.exhaustive().filter { it.windowSize.width > 1280.dp || it.windowSize.height > 800.dp }

    assertThat(tooBig).isEmpty()
  }

  private fun pairs(configs: List<StressConfig>): Set<Pair<Any?, Any?>> {
    return configs.flatMapTo(mutableSetOf()) { config ->
      val device = config.name.substringBefore(" + ")
      val values = listOf(device, config.name.contains("display"), config.fontScale, config.layoutDirection, config.locale)
      values.indices.flatMap { a -> (a + 1 until values.size).map { b -> (a to values[a]) to (b to values[b]) } }
    }
  }
}

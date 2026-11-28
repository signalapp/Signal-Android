/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StressTestPreviewTest {

  private val source = File("../core/ui/src/main/java/org/signal/core/ui/compose/StressTestPreview.kt")

  @Test
  fun `StressTestPreview previews every default config`() {
    val expected = StressConfig.Default.map { it.toPreviewAnnotation(group = PreviewTemplate.STRESS_TEST_PREVIEW_GROUP) }
    val actual = source.readLines().filter { it.startsWith("@Preview(") }

    assertEquals("${source.name} is out of date with StressConfig.Default. Replace its previews with:\n${expected.joinToString("\n")}\n", expected, actual)
  }
}

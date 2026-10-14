/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

/**
 * Describes the preview function that a failure message suggests, so the problem can be reproduced in the IDE.
 *
 * [functionName] names the suggested preview function, [annotations] are added above its `@Preview`s, and
 * [contentHint] goes in its body to say what content to render.
 */
data class PreviewTemplate(
  val functionName: String = "LayoutStressPreview",
  val annotations: List<String> = emptyList(),
  val contentHint: String = "The content that failed"
) {
  internal fun render(configs: List<StressConfig>): String {
    return buildString {
      configs.forEach { appendLine(it.toPreviewAnnotation()) }
      annotations.forEach { appendLine(it) }
      appendLine("@Composable")
      appendLine("private fun $functionName() {")
      appendLine("  // $contentHint")
      append("}")
    }
  }
}

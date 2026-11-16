/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

/**
 * Describes the preview function that a failure message suggests, so the problem can be reproduced in the IDE.
 *
 * [functionName] names the suggested preview function, [annotations] are added below its preview annotations, and
 * [contentHint] goes in its body to say what content to render.
 */
data class PreviewTemplate(
  val functionName: String = "LayoutStressPreview",
  val annotations: List<String> = emptyList(),
  val contentHint: String = "The content that failed"
) {

  companion object {
    /** The multi-preview annotation in core/ui that previews every config in [StressConfig.Default]. */
    const val STRESS_TEST_PREVIEW = "@StressTestPreview"

    /** The preview group [STRESS_TEST_PREVIEW]'s previews are in. */
    const val STRESS_TEST_PREVIEW_GROUP = "stress test"
  }

  /**
   * A test that ran the default configs is reproduced with [STRESS_TEST_PREVIEW], pointing at the previews of
   * [failingConfigs]. Any other test gets one preview per failing config.
   */
  internal fun render(failingConfigs: List<StressConfig>, allConfigs: List<StressConfig>): String {
    val usesStressTestPreview = allConfigs == StressConfig.Default

    return buildString {
      if (usesStressTestPreview) {
        appendLine("Reproduce in the IDE with this preview, and look at its ${failingConfigs.joinToString { "\"${it.name}\"" }} previews:")
      } else {
        appendLine("Reproduce in the IDE with a preview like this:")
      }
      appendLine()

      if (usesStressTestPreview) {
        appendLine(STRESS_TEST_PREVIEW)
      } else {
        failingConfigs.forEach { appendLine(it.toPreviewAnnotation()) }
      }
      annotations.forEach { appendLine(it) }
      appendLine("@Composable")
      appendLine("private fun $functionName() {")
      appendLine("  // $contentHint")
      append("}")
    }
  }
}

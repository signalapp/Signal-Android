/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import org.robolectric.annotation.GraphicsMode
import org.robolectric.config.ConfigurationRegistry

/**
 * Renders [content] once under each of [configs] and fails if any of [checks] finds a layout problem,
 * listing every problem found across all configs.
 *
 * The checks read the semantics tree rather than pixels, so they run on the JVM and need no reference images.
 *
 * This calls [ComposeContentTestRule.setContent], so it must be the only content set in the test.
 *
 * @param ignoreTags Nodes tagged with one of these, and everything inside them, are skipped. Use this for content
 * that is meant to clip, like a marquee.
 */
fun ComposeContentTestRule.assertPassesLayoutChecks(
  configs: List<StressConfig> = StressConfig.Default,
  checks: Set<LayoutCheck> = LayoutCheck.entries.toSet(),
  ignoreTags: Set<String> = emptySet(),
  previewTemplate: PreviewTemplate = PreviewTemplate(),
  content: @Composable () -> Unit
) {
  val problems = findLayoutProblems(configs, checks, ignoreTags, content)
  if (problems.isNotEmpty()) {
    throw AssertionError(failureMessage(problems, configs, previewTemplate))
  }
}

/**
 * Same as [assertPassesLayoutChecks], but returns the problems instead of failing.
 */
fun ComposeContentTestRule.findLayoutProblems(
  configs: List<StressConfig> = StressConfig.Default,
  checks: Set<LayoutCheck> = LayoutCheck.entries.toSet(),
  ignoreTags: Set<String> = emptySet(),
  content: @Composable () -> Unit
): List<LayoutProblem> {
  require(configs.isNotEmpty()) { "At least one config is required" }
  check(!isRobolectricLegacyGraphics()) {
    "Robolectric's LEGACY graphics mode fakes text measurement, so layout checks would be meaningless. " +
      "Annotate the test class with @GraphicsMode(GraphicsMode.Mode.NATIVE)."
  }

  var current by mutableStateOf(configs.first())
  var realWindow = DpSize.Unspecified
  setContent {
    val windowInfo = LocalWindowInfo.current
    SideEffect { realWindow = windowInfo.containerDpSize }

    DeviceConfigurationOverride(current.override) {
      FillWindow(content)
    }
  }

  val problems = mutableListOf<LayoutProblem>()
  val textSizesByConfig = mutableMapOf<StressConfig, Map<String, TextSize>>()

  for (config in configs) {
    current = config
    waitForIdle()

    check(config.windowSize.width <= realWindow.width && config.windowSize.height <= realWindow.height) {
      "\"${config.name}\" needs a ${config.windowSize} window but the test's is $realWindow. " +
        "Annotate the test class with @Config(qualifiers = StressConfig.ROBOLECTRIC_QUALIFIERS)."
    }

    val textSizes = mutableMapOf<String, TextSize>()
    val window = onNodeWithTag(WINDOW_TAG, useUnmergedTree = true).fetchSemanticsNode()
    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEachIndexed { index, root ->
      val isWindow = window.ancestors.any { it.id == root.id }

      // Dialogs and popups are separate windows that take the real configuration rather than the stressed one,
      // so they're checked once and never compared across configs
      if (!isWindow && config != configs.first()) {
        return@forEachIndexed
      }

      val inspector = LayoutInspector(if (isWindow) window else root, rootPath = index.toString(), ignoreTags)
      problems += inspector.findProblems(config, checks)
      if (isWindow) {
        textSizes += inspector.textSizes()
      }
    }
    textSizesByConfig[config] = textSizes
  }

  if (LayoutCheck.TEXT_DOES_NOT_SCALE in checks) {
    problems += findUnscaledText(configs, textSizesByConfig)
  }

  return problems
}

private fun failureMessage(problems: List<LayoutProblem>, configs: List<StressConfig>, previewTemplate: PreviewTemplate): String {
  val grouped = problems
    .groupBy { it.check to it.nodePath }
    .values
    .map { sameProblem -> "[${sameProblem.joinToString { it.config.name }}] ${sameProblem.first().message}" }

  val failingConfigs = configs.filter { config -> problems.any { it.config == config } }

  return buildString {
    appendLine("Layout checks failed, ${grouped.size} problem(s):")
    grouped.forEach { appendLine("  - $it") }
    appendLine()
    appendLine("Reproduce in the IDE with a preview like this:")
    appendLine()
    append(previewTemplate.render(failingConfigs))
  }
}

private const val WINDOW_TAG = "org.signal.screener.window"

/**
 * Takes up the whole (possibly overridden) window so its bounds are the screen's rather than just the
 * content's, and places the content at the top start like a screen would.
 */
@Composable
private fun FillWindow(content: @Composable () -> Unit) {
  Layout(content = content, modifier = Modifier.testTag(WINDOW_TAG)) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      placeables.forEach { it.placeRelative(0, 0) }
    }
  }
}

private fun isRobolectricLegacyGraphics(): Boolean {
  return Build.FINGERPRINT == "robolectric" && ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.LEGACY
}

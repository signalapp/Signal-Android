/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base class for layout stress tests. Subclasses only need a test per state:
 *
 * ```
 * class MyScreenLayoutTest : LayoutStressTest() {
 *   @Test
 *   fun `empty state`() = assertPassesLayoutChecks { MyScreen(MyState()) }
 * }
 * ```
 *
 * Override [Wrap] to provide a theme, and [configs] to change what every test in the class runs under.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = StressConfig.ROBOLECTRIC_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LayoutStressTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val testName = TestName()

  protected open val configs: List<StressConfig> = StressConfig.Default

  /** Annotations the suggested reproduction preview needs, e.g. one that provides the theme [Wrap] does. */
  protected open val previewAnnotations: List<String> = emptyList()

  @Composable
  protected open fun Wrap(content: @Composable () -> Unit) {
    content()
  }

  protected fun assertPassesLayoutChecks(
    checks: Set<LayoutCheck> = LayoutCheck.entries.toSet(),
    ignoreTags: Set<String> = emptySet(),
    content: @Composable () -> Unit
  ) {
    val previewTemplate = PreviewTemplate(
      functionName = javaClass.simpleName.removeSuffix("Test") + "Preview",
      annotations = previewAnnotations,
      contentHint = "Same content as ${javaClass.simpleName}.`${testName.methodName}`"
    )

    composeTestRule.assertPassesLayoutChecks(configs, checks, ignoreTags, previewTemplate) { Wrap(content) }
  }

  protected fun findLayoutProblems(
    configs: List<StressConfig> = this.configs,
    checks: Set<LayoutCheck> = LayoutCheck.entries.toSet(),
    ignoreTags: Set<String> = emptySet(),
    content: @Composable () -> Unit
  ): List<LayoutProblem> {
    return composeTestRule.findLayoutProblems(configs, checks, ignoreTags) { Wrap(content) }
  }
}

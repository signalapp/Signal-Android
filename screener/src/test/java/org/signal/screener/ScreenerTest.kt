/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.Test
import org.robolectric.annotation.Config

class ScreenerTest : LayoutStressTest() {

  companion object {
    private const val LONG_TEXT = "A label that is much too long to fit on one line of a narrow container"
  }

  @Test
  fun `wrapping text in an unconstrained column has no problems`() {
    val problems = composeTestRule.findLayoutProblems {
      Column(modifier = Modifier.width(200.dp)) {
        Text(text = LONG_TEXT)
        Text(text = "Second")
      }
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `text in a fixed height container overflows`() {
    val checks = checksFound {
      Box(modifier = Modifier.size(width = 80.dp, height = 20.dp)) {
        Text(text = LONG_TEXT)
      }
    }

    assertThat(checks).contains(LayoutCheck.TEXT_OVERFLOW)
  }

  @Test
  fun `text clipped by maxLines without an ellipsis overflows`() {
    val checks = checksFound {
      Text(text = LONG_TEXT, maxLines = 1, modifier = Modifier.width(80.dp))
    }

    assertThat(checks).contains(LayoutCheck.TEXT_OVERFLOW)
  }

  @Test
  fun `a word too wide for its container is split`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Text(text = "Unbelievably", modifier = Modifier.width(40.dp))
    }

    assertThat(problems.map { it.check }).containsExactly(LayoutCheck.WORD_SPLIT)
    assertThat(problems.single().message).contains("\"Unbelievably\"")
  }

  @Test
  fun `wrapping between words is not a split`() {
    val checks = checksFound {
      Text(text = LONG_TEXT, modifier = Modifier.width(200.dp))
    }

    assertThat(checks).doesNotContain(LayoutCheck.WORD_SPLIT)
  }

  @Test
  fun `hyphenated text may split words`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Text(text = "Unbelievably", style = TextStyle(hyphens = Hyphens.Auto), modifier = Modifier.width(40.dp))
    }

    assertThat(problems.map { it.check }).doesNotContain(LayoutCheck.WORD_SPLIT)
  }

  @Test
  fun `scripts without spaces may break anywhere`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Text(text = "絵文字を検索するための長い説明文", modifier = Modifier.width(60.dp))
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `text that ellipsizes does not overflow`() {
    val checks = checksFound {
      Text(text = LONG_TEXT, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp))
    }

    assertThat(checks).doesNotContain(LayoutCheck.TEXT_OVERFLOW)
  }

  @Test
  fun `text measured larger than its container extends outside it`() {
    val checks = checksFound {
      Box(modifier = Modifier.padding(top = 150.dp).size(width = 80.dp, height = 20.dp)) {
        Text(text = "Too tall for its box", modifier = Modifier.width(80.dp).wrapContentHeight(unbounded = true))
      }
    }

    assertThat(checks).containsOnly(LayoutCheck.TEXT_OUTSIDE_CONTAINER)
  }

  @Test
  fun `content that grows past the screen without scrolling is off screen`() {
    val checks = checksFound {
      Column(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) {
        repeat(40) { Text(text = "Item $it") }
      }
    }

    assertThat(checks).contains(LayoutCheck.OFF_SCREEN)
  }

  @Test
  fun `content that grows past the screen inside a scroll container has no problems`() {
    val problems = composeTestRule.findLayoutProblems {
      Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        repeat(40) { Text(text = "Item $it") }
      }
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `stacked text overlaps`() {
    val checks = checksFound {
      StackedText()
    }

    assertThat(checks).contains(LayoutCheck.OVERLAP)
  }

  @Test
  fun `stacked clickables overlap`() {
    val checks = checksFound {
      Box {
        Box(modifier = Modifier.size(48.dp).clickable {})
        Box(modifier = Modifier.size(48.dp).clickable {})
      }
    }

    assertThat(checks).contains(LayoutCheck.OVERLAP)
  }

  @Test
  fun `text inside its clickable parent does not overlap`() {
    val checks = checksFound {
      Box(modifier = Modifier.clickable {}) {
        Text(text = "Hello")
      }
    }

    assertThat(checks).doesNotContain(LayoutCheck.OVERLAP)
  }

  @Test
  fun `text sized from dp does not scale`() {
    val checks = checksFound {
      Text(text = "Hello", fontSize = with(LocalDensity.current) { 14.dp.toSp() })
    }

    assertThat(checks).contains(LayoutCheck.TEXT_DOES_NOT_SCALE)
  }

  @Test
  fun `text sized in sp scales`() {
    val checks = checksFound {
      Text(text = "Hello", fontSize = 14.sp)
    }

    assertThat(checks).doesNotContain(LayoutCheck.TEXT_DOES_NOT_SCALE)
  }

  @Test
  fun `a problem that only appears at a large font scale is attributed to that config`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline, StressConfig.LargestFont)) {
      Box(modifier = Modifier.size(width = 200.dp, height = 24.dp)) {
        Text(text = "Fits at 1x", fontSize = 14.sp)
      }
    }

    assertThat(problems.filter { it.config == StressConfig.Baseline }).isEmpty()
    assertThat(problems.map { it.check to it.config }).contains(LayoutCheck.TEXT_OVERFLOW to StressConfig.LargestFont)
  }

  @Test
  fun `a locale config renders that locale's resource strings`() {
    val problems = composeTestRule.findLayoutProblems(
      configs = listOf(StressConfig.Baseline, StressConfig(name = "de", locale = "de")),
      checks = setOf(LayoutCheck.TEXT_OVERFLOW)
    ) {
      // "Cancel" fits, "Abbrechen" doesn't
      Text(text = stringResource(android.R.string.cancel), maxLines = 1, modifier = Modifier.width(52.dp))
    }

    assertThat(problems.map { it.config.name }).containsExactly("de")
  }

  @Test
  fun `content off the top is fine when something on screen can scroll up`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      ScrolledPastTopBar(initialScroll = 50)
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `content off the top is off screen when nothing can scroll up`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      ScrolledPastTopBar(initialScroll = 0)
    }

    assertThat(problems.map { it.check }).containsExactly(LayoutCheck.OFF_SCREEN)
    assertThat(problems.single().message).contains("off the top of the screen")
  }

  @Test
  fun `content that only fits in portrait is off screen in landscape`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline, StressConfig.LargestFontLandscape)) {
      Column {
        Box(modifier = Modifier.height(420.dp))
        Text(text = "Continue")
      }
    }

    assertThat(problems.map { it.check to it.config }).containsExactly(LayoutCheck.OFF_SCREEN to StressConfig.LargestFontLandscape)
  }

  @Test
  fun `text that fits a phone is cut off on a small phone`() {
    val smallPhone = StressConfig(name = "small phone", windowSize = StressConfig.WindowSizes.SmallPhone)

    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline, smallPhone)) {
      Row {
        Box(modifier = Modifier.width(230.dp))
        Text(text = "Continue", maxLines = 1)
      }
    }

    assertThat(problems.map { it.check to it.config }).containsExactly(LayoutCheck.TEXT_OVERFLOW to smallPhone)
  }

  @Test
  fun `a window smaller than the test's real window bounds the screen`() {
    val tiny = StressConfig(name = "tiny", windowSize = DpSize(100.dp, 100.dp))

    val problems = composeTestRule.findLayoutProblems(configs = listOf(tiny)) {
      Column {
        Box(modifier = Modifier.height(150.dp))
        Text(text = "Continue")
      }
    }

    assertThat(problems.map { it.check }).containsExactly(LayoutCheck.OFF_SCREEN)
  }

  @Test
  fun `window size classes see the overridden window`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline, StressConfig.LargestFontTablet)) {
      if (LocalConfiguration.current.screenWidthDp >= 840 && LocalWindowInfo.current.containerDpSize.width >= 840.dp) {
        StackedText()
      }
    }

    assertThat(problems.map { it.check to it.config }).containsExactly(LayoutCheck.OVERLAP to StressConfig.LargestFontTablet)
  }

  @Test
  @Config(qualifiers = "w320dp-h470dp")
  fun `refuses to run when the test's window is smaller than a config's`() {
    assertFailure {
      composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) { Text(text = "Hello") }
    }.isInstanceOf<IllegalStateException>().messageContains("StressConfig.ROBOLECTRIC_QUALIFIERS")
  }

  @Test
  fun `breakpoints computed from display metrics see the overridden window`() {
    val problems = findLayoutProblems(configs = listOf(StressConfig.Baseline, StressConfig.LargestFontTablet)) {
      val metrics = LocalResources.current.displayMetrics
      if (metrics.widthPixels / metrics.density >= 840) {
        StackedText()
      }
    }

    assertThat(problems.map { it.check to it.config }).containsExactly(LayoutCheck.OVERLAP to StressConfig.LargestFontTablet)
  }

  @Test
  fun `dialogs are checked once without stress`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline, StressConfig.LargestFont)) {
      Dialog(onDismissRequest = {}) {
        StackedText()
      }
    }

    assertThat(problems.map { it.check to it.config }).containsExactly(LayoutCheck.OVERLAP to StressConfig.Baseline)
  }

  @Test
  fun `content mostly on screen is not off screen`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Box(modifier = Modifier.offset(y = (-10).dp).size(48.dp).clickable {})
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `content clipped completely out of sight is not checked`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Box(modifier = Modifier.padding(top = 100.dp).size(width = 200.dp, height = 40.dp).clipToBounds()) {
        Text(text = "Collapsed header", modifier = Modifier.offset(y = (-60).dp))
      }
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `content clipped partly out of sight is still checked`() {
    val checks = checksFound(checks = setOf(LayoutCheck.TEXT_OUTSIDE_CONTAINER)) {
      Box(modifier = Modifier.padding(top = 100.dp).size(width = 200.dp, height = 40.dp).clipToBounds()) {
        Text(text = "Half hidden", modifier = Modifier.offset(y = (-8).dp))
      }
    }

    assertThat(checks).containsOnly(LayoutCheck.TEXT_OUTSIDE_CONTAINER)
  }

  @Test
  fun `ignored tags skip everything inside them`() {
    val problems = composeTestRule.findLayoutProblems(ignoreTags = setOf("marquee")) {
      Box(modifier = Modifier.testTag("marquee")) {
        StackedText()
      }
    }

    assertThat(problems).isEmpty()
  }

  @Test
  fun `only the requested checks run`() {
    val checks = checksFound(checks = setOf(LayoutCheck.OVERLAP)) {
      Box(modifier = Modifier.size(width = 80.dp, height = 20.dp)) {
        Text(text = LONG_TEXT)
      }
    }

    assertThat(checks).isEmpty()
  }

  @Test
  fun `assertPassesLayoutChecks lists each problem with its config`() {
    assertFailure {
      composeTestRule.assertPassesLayoutChecks(configs = listOf(StressConfig.Baseline)) {
        StackedText()
      }
    }.isInstanceOf<AssertionError>().messageContains("[baseline] Text overlap")
  }

  @Test
  fun `text squeezed off screen is only reported as off screen`() {
    val problems = composeTestRule.findLayoutProblems(configs = listOf(StressConfig.Baseline)) {
      Column {
        Box(modifier = Modifier.height(2000.dp))
        Text(text = "Continue")
      }
    }

    assertThat(problems.map { it.check }).containsExactly(LayoutCheck.OFF_SCREEN)
  }

  @Test
  fun `assertPassesLayoutChecks reports a problem shared by several configs once`() {
    assertFailure {
      composeTestRule.assertPassesLayoutChecks {
        StackedText()
      }
    }.isInstanceOf<AssertionError>().all {
      messageContains("1 problem(s)")
      messageContains("[${StressConfig.Default.joinToString { it.name }}] Text overlap")
    }
  }

  @Test
  fun `failure message suggests a preview for only the failing configs`() {
    assertFailure {
      composeTestRule.assertPassesLayoutChecks(configs = listOf(StressConfig.Baseline, StressConfig.LargestFont)) {
        Box(modifier = Modifier.size(width = 200.dp, height = 24.dp)) {
          Text(text = "Fits at 1x", fontSize = 14.sp)
        }
      }
    }.isInstanceOf<AssertionError>().all {
      messageContains(StressConfig.LargestFont.toPreviewAnnotation())
      transform { it.message!! }.doesNotContain(StressConfig.Baseline.toPreviewAnnotation())
    }
  }

  @Test
  fun `base class failures name the test in the suggested preview`() {
    assertFailure {
      assertPassesLayoutChecks { StackedText() }
    }.isInstanceOf<AssertionError>().all {
      messageContains("private fun ScreenerPreview()")
      messageContains("// Same content as ScreenerTest.`base class failures name the test in the suggested preview`")
    }
  }

  @Test
  fun `assertPassesLayoutChecks passes when there are no problems`() {
    composeTestRule.assertPassesLayoutChecks {
      Text(text = "Hello")
    }
  }

  /** A bar half above the screen, like a collapsing top bar, over content scrolled down by [initialScroll]. */
  @Composable
  private fun ScrolledPastTopBar(initialScroll: Int) {
    Box {
      Column(modifier = Modifier.padding(top = 48.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState(initialScroll))) {
        repeat(40) { Text(text = "Item $it") }
      }
      Box(modifier = Modifier.offset(y = (-20).dp)) {
        Text(text = "Title")
      }
    }
  }

  @Composable
  private fun StackedText() {
    Box {
      Text(text = "Hello")
      Text(text = "World")
    }
  }

  private fun checksFound(checks: Set<LayoutCheck> = LayoutCheck.entries.toSet(), content: @Composable () -> Unit): Set<LayoutCheck> {
    return composeTestRule.findLayoutProblems(checks = checks, content = content).map { it.check }.toSet()
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class PreviewTemplateTest {

  @Test
  fun `baseline config only sets the device`() {
    assertThat(StressConfig.Baseline.toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "baseline", device = "spec:width=360dp,height=780dp")""")
  }

  @Test
  fun `font scale and locale carry over`() {
    assertThat(StressConfig(name = "font 2x + de", fontScale = 2f, locale = "de").toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "font 2x + de", device = "spec:width=360dp,height=780dp", fontScale = 2f, locale = "de")""")
  }

  @Test
  fun `fractional font scales keep their fraction`() {
    assertThat(StressConfig(name = "font 1.3x", fontScale = 1.3f).toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "font 1.3x", device = "spec:width=360dp,height=780dp", fontScale = 1.3f)""")
  }

  @Test
  fun `rtl becomes english in arabic script`() {
    assertThat(StressConfig(name = "font 2x + RTL", fontScale = 2f, layoutDirection = LayoutDirection.Rtl).toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "font 2x + RTL", device = "spec:width=360dp,height=780dp", fontScale = 2f, locale = "b+en+Arab")""")
  }

  @Test
  fun `rtl with a regional locale keeps the region`() {
    val config = StressConfig(name = "rtl pt-BR", windowSize = DpSize(780.dp, 360.dp), layoutDirection = LayoutDirection.Rtl, locale = "pt-BR")

    assertThat(config.toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "rtl pt-BR", device = "spec:width=780dp,height=360dp", locale = "b+pt+Arab+BR")""")
  }

  @Test
  fun `template lists the failing configs above its function`() {
    val template = PreviewTemplate(functionName = "MyScreenPreview", annotations = listOf("@PreviewWrapper(SignalPreviewWrapper::class)"), contentHint = "MyScreen()")

    val configs = listOf(StressConfig.Baseline, StressConfig.LargestFontLandscape)

    assertThat(template.render(failingConfigs = configs, allConfigs = configs)).isEqualTo(
      """
      Reproduce in the IDE with a preview like this:

      @Preview(name = "baseline", device = "spec:width=360dp,height=780dp")
      @Preview(name = "font 2x + landscape", device = "spec:width=780dp,height=360dp", fontScale = 2f)
      @PreviewWrapper(SignalPreviewWrapper::class)
      @Composable
      private fun MyScreenPreview() {
        // MyScreen()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `template for the default configs uses StressTestPreview and names the failing previews`() {
    val template = PreviewTemplate(functionName = "MyScreenPreview", annotations = listOf("@PreviewWrapper(SignalPreviewWrapper::class)"), contentHint = "MyScreen()")
    val failing = StressConfig.Default.take(2)

    assertThat(template.render(failingConfigs = failing, allConfigs = StressConfig.Default)).isEqualTo(
      """
      Reproduce in the IDE with this preview, and look at its "${failing[0].name}", "${failing[1].name}" previews:

      @StressTestPreview
      @PreviewWrapper(SignalPreviewWrapper::class)
      @Composable
      private fun MyScreenPreview() {
        // MyScreen()
      }
      """.trimIndent()
    )
  }

  @Test
  fun `group is added last`() {
    assertThat(StressConfig.Baseline.toPreviewAnnotation(group = "stress test"))
      .isEqualTo("""@Preview(name = "baseline", device = "spec:width=360dp,height=780dp", group = "stress test")""")
  }

  @Test
  fun `pseudo-languages become bcp-47 qualifiers`() {
    val config = StressConfig(name = "pseudo-compound + RTL", layoutDirection = LayoutDirection.Rtl, locale = PseudoLanguage.COMPOUND.locale)

    assertThat(config.toPreviewAnnotation())
      .isEqualTo("""@Preview(name = "pseudo-compound + RTL", device = "spec:width=360dp,height=780dp", locale = "b+qab+Arab")""")
  }
}

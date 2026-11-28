/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Application
import android.graphics.Typeface
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TextsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `bold and italic spans become font styles at the same offsets`() {
    val spanned = SpannableString("plain bold italic").apply {
      setSpan(StyleSpan(Typeface.BOLD), 6, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      setSpan(StyleSpan(Typeface.ITALIC), 11, 17, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val result = convert(spanned)

    assertThat(result.text).isEqualTo("plain bold italic")
    assertThat(result.spanStyles.map { Triple(it.start, it.end, it.item.fontWeight ?: it.item.fontStyle) })
      .containsExactly(Triple(6, 10, FontWeight.Bold), Triple(11, 17, FontStyle.Italic))
  }

  @Test
  fun `url spans keep their url as an annotation`() {
    val spanned = SpannableString("see the docs").apply {
      setSpan(URLSpan("https://example.org"), 8, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val result = convert(spanned)

    assertThat(result.getStringAnnotations("URL", 8, 12).map { it.item }).containsExactly("https://example.org")
  }

  @Test
  fun `html markup round trips through the converter`() {
    val spanned = Html.fromHtml("Do not <b>share</b> it.", Html.FROM_HTML_MODE_LEGACY)

    val result = convert(spanned)

    assertThat(result.text).isEqualTo("Do not share it.")
    val bold = result.spanStyles.single()
    assertThat(bold.item.fontWeight).isEqualTo(FontWeight.Bold)
    assertThat(result.text.substring(bold.start, bold.end)).isEqualTo("share")
  }

  private fun convert(spanned: Spanned): AnnotatedString {
    var result: AnnotatedString? = null
    composeTestRule.setContent {
      SignalTheme {
        result = Texts.annotatedString(spanned)
      }
    }
    assertThat(result).isNotNull()
    return result!!
  }
}

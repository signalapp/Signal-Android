/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.getSpans
import org.signal.core.ui.R

object Texts {
  /**
   * Header row for settings pages.
   *
   * @param trailingContent Optional content shown beside the header text, such as a status tag.
   */
  @Composable
  fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {}
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = modifier
        .padding(
          horizontal = dimensionResource(id = R.dimen.gutter)
        )
        .padding(top = 16.dp, bottom = 12.dp)
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f, fill = false)
      )

      trailingContent()
    }
  }

  @Composable
  fun LinkifiedText(
    textWithUrlSpans: Spanned,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
  ) {
    val annotatedText = annotatedString(textWithUrlSpans)
    ClickableText(
      text = annotatedText,
      style = style,
      modifier = modifier,
      onClick = { offset ->
        annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
          onUrlClick(annotation.item)
        }
      }
    )
  }

  /**
   * Loads a string resource that uses inline markup (`<b>`, `<i>`, `<u>`, `<a href>`) and keeps that markup as styles.
   * Format arguments are not supported, since formatting strips the markup.
   */
  @Composable
  fun annotatedStringResource(@StringRes id: Int): AnnotatedString {
    val text = LocalContext.current.resources.getText(id)
    return if (text is Spanned) annotatedString(text) else AnnotatedString(text.toString())
  }

  /** Converts [spanned] to an [AnnotatedString], keeping bold, italic, underline and link spans. Links are tagged with the "URL" annotation. */
  @Composable
  fun annotatedString(spanned: Spanned): AnnotatedString {
    val builder = AnnotatedString.Builder(spanned.toString())
    for (span in spanned.getSpans<CharacterStyle>()) {
      val start = spanned.getSpanStart(span)
      val end = spanned.getSpanEnd(span)
      when (span) {
        is StyleSpan -> {
          when (span.style) {
            Typeface.BOLD -> {
              builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            Typeface.ITALIC -> {
              builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
            Typeface.BOLD_ITALIC -> {
              builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
          }
        }
        is UnderlineSpan -> {
          builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
        is URLSpan -> {
          builder.addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary), start, end)
          builder.addStringAnnotation("URL", span.url, start, end)
        }
      }
    }
    return builder.toAnnotatedString()
  }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
  Previews.Preview {
    Texts.SectionHeader("Header")
  }
}

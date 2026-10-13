/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.text.Spanned
import android.text.style.URLSpan
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
    val annotatedText = annotatedStringFromUrlSpans(urlSpanText = textWithUrlSpans)
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

  @Composable
  private fun annotatedStringFromUrlSpans(urlSpanText: Spanned): AnnotatedString {
    val builder = AnnotatedString.Builder(urlSpanText.toString())
    val urlSpans = urlSpanText.getSpans<URLSpan>()
    for (urlSpan in urlSpans) {
      val spanStart = urlSpanText.getSpanStart(urlSpan)
      val spanEnd = urlSpanText.getSpanEnd(urlSpan)
      builder.addStyle(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary),
        start = spanStart,
        end = spanEnd
      )
      builder.addStringAnnotation("URL", urlSpan.url, spanStart, spanEnd)
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

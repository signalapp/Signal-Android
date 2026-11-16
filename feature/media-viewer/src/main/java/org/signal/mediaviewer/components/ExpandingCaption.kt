/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.mediaviewer.MediaViewerPreviewFixtures
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.R
import org.signal.uicomponents.spoiler.SpoilerState
import org.signal.uicomponents.styledtext.StyleRange
import org.signal.uicomponents.styledtext.StyledString
import org.signal.uicomponents.styledtext.StyledText

/**
 * Media caption that collapses to [collapsedMaxLines] with a "Read more" suffix and expands on tap.
 */
@Composable
internal fun ExpandingCaption(
  caption: StyledString,
  modifier: Modifier = Modifier,
  collapsedMaxLines: Int = COLLAPSED_MAX_LINES,
  maxExpandedHeightFraction: Float = MAX_EXPANDED_HEIGHT_FRACTION
) {
  var expanded by rememberSaveable(caption.text) { mutableStateOf(false) }

  // -1 until measured, and stays -1 for captions that fit.
  var truncateAt by rememberSaveable(caption.text) { mutableIntStateOf(-1) }
  var lastLineStart by rememberSaveable(caption.text) { mutableIntStateOf(0) }
  val isExpandable = truncateAt >= 0
  val showReadMore = !expanded && isExpandable

  val spoilerState = remember(caption) { SpoilerState() }
  val readMore = stringResource(R.string.MediaViewerScreen__read_more)
  val readMoreSuffix = remember(readMore) {
    buildAnnotatedString {
      withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
        append(readMore)
      }
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val maxExpandedHeight = maxHeight * maxExpandedHeightFraction

    val displayed = if (showReadMore) {
      caption.copy(text = caption.text.take((truncateAt - READ_MORE_CHAR_HEADROOM).coerceAtLeast(lastLineStart)).trimEnd(' ', '\t'))
    } else {
      caption
    }

    StyledText(
      text = displayed,
      spoilerState = spoilerState,
      suffix = if (showReadMore) readMoreSuffix else null,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
      overflow = TextOverflow.Clip,
      onTextLayout = { layout ->
        val result = layout.textLayoutResult
        if (!expanded && result.hasVisualOverflow) {
          truncateAt = layout.toTextIndex(result.getLineEnd(collapsedMaxLines - 1, visibleEnd = true)).coerceAtMost(displayed.text.length)
          lastLineStart = layout.toTextIndex(result.getLineStart(collapsedMaxLines - 1)).coerceAtMost(displayed.text.length)
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize()
        .heightIn(max = if (expanded) maxExpandedHeight else Dp.Unspecified)
        .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          enabled = isExpandable
        ) { expanded = !expanded }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag(MediaViewerScreenTestTags.CAPTION)
    )
  }
}

private const val COLLAPSED_MAX_LINES = 3
private const val READ_MORE_CHAR_HEADROOM = 14
private const val MAX_EXPANDED_HEIGHT_FRACTION = 0.7f

@DayNightPreviews
@Composable
private fun ExpandingCaptionPreview() {
  Previews.Preview {
    ExpandingCaption(caption = StyledString("A short caption."))
  }
}

@DayNightPreviews
@Composable
private fun ExpandingCaptionLongPreview() {
  Previews.Preview {
    ExpandingCaption(caption = StyledString(MediaViewerPreviewFixtures.LOREM_IPSUM))
  }
}

@DayNightPreviews
@Composable
private fun ExpandingCaptionStyledPreview() {
  Previews.Preview {
    ExpandingCaption(
      caption = StyledString(
        text = "Bold, italic, struck, mono, and a secret spoiler.",
        styles = listOf(
          StyleRange(0, 4, StyleRange.Style.BOLD),
          StyleRange(6, 12, StyleRange.Style.ITALIC),
          StyleRange(14, 20, StyleRange.Style.STRIKETHROUGH),
          StyleRange(22, 26, StyleRange.Style.MONOSPACE),
          StyleRange(41, 48, StyleRange.Style.SPOILER)
        )
      )
    )
  }
}

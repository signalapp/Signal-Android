/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.ContentTypeUtil
import org.signal.core.util.isNotNullOrBlank
import org.signal.mediasend.R

@Composable
internal fun MediaEditSummaryPill(
  displayName: String?,
  selectedMedia: List<Media>,
  selectedPage: Int,
  modifier: Modifier = Modifier
) {
  val selectedMediaText = rememberSelectedMediaText(selectedMedia, selectedPage)

  val targetState = remember(displayName, selectedMediaText) {
    val hasDisplayName = displayName.isNotNullOrBlank()
    val hasSelectedMedia = selectedMediaText.isNotNullOrBlank()

    when {
      hasDisplayName && hasSelectedMedia -> {
        MediaEditSummaryPillTargetState.MultiRow(
          row1 = displayName,
          row2 = selectedMediaText
        )
      }

      hasDisplayName -> {
        MediaEditSummaryPillTargetState.SingleRow(
          text = displayName
        )
      }

      hasSelectedMedia -> {
        MediaEditSummaryPillTargetState.SingleRow(
          text = selectedMediaText
        )
      }

      else -> MediaEditSummaryPillTargetState.None
    }
  }

  Box(
    modifier = modifier
  ) {
    when (targetState) {
      is MediaEditSummaryPillTargetState.MultiRow -> MultiRowPill(targetState)
      MediaEditSummaryPillTargetState.None -> Unit
      is MediaEditSummaryPillTargetState.SingleRow -> SingleRowPill(targetState)
    }
  }
}

@Composable
private fun SingleRowPill(singleRow: MediaEditSummaryPillTargetState.SingleRow) {
  Pill(
    modifier = Modifier.padding(
      horizontal = 12.dp,
      vertical = 8.dp
    )
  ) {
    Text(text = singleRow.text, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun MultiRowPill(multiRow: MediaEditSummaryPillTargetState.MultiRow) {
  Pill(
    modifier = Modifier.padding(
      horizontal = 16.dp,
      vertical = 4.dp
    )
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(text = multiRow.row1, style = MaterialTheme.typography.bodyMedium)
      Text(text = multiRow.row2, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight(400)))
    }
  }
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape
      )
      .then(modifier)
  ) {
    content()
  }
}

@Composable
fun rememberSelectedMediaText(selectedMedia: List<Media>, selectedPage: Int): String {
  if (selectedMedia.isEmpty()) {
    return ""
  }

  if (selectedMedia.size > 1) {
    return stringResource(R.string.MediaEditScreen__d_of_d, selectedPage + 1, selectedMedia.size)
  }

  val media = selectedMedia.first()
  val contentType = media.contentType

  val label = when {
    ContentTypeUtil.isGif(contentType) || media.isVideoGif -> R.plurals.MediaEditScreen__gif
    ContentTypeUtil.isImageType(contentType) -> R.plurals.MediaEditScreen__photo
    ContentTypeUtil.isVideoType(contentType) -> R.plurals.MediaEditScreen__video
    ContentTypeUtil.isDocumentType(contentType) -> R.plurals.MediaEditScreen__document
    else -> R.plurals.MediaEditScreen__item
  }

  return pluralStringResource(label, selectedMedia.size, selectedMedia.size)
}

@DayNightPreviews
@Composable
private fun SingleRowPillPreview() {
  Previews.Preview {
    SingleRowPill(singleRow = MediaEditSummaryPillTargetState.SingleRow(text = "Single Row"))
  }
}

@DayNightPreviews
@Composable
private fun MultiRowPillPreview() {
  Previews.Preview {
    MultiRowPill(multiRow = MediaEditSummaryPillTargetState.MultiRow(row1 = "Top Row", row2 = "Bottom Row"))
  }
}

private sealed interface MediaEditSummaryPillTargetState {
  data class SingleRow(val text: String) : MediaEditSummaryPillTargetState
  data class MultiRow(val row1: String, val row2: String) : MediaEditSummaryPillTargetState
  data object None : MediaEditSummaryPillTargetState
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

private val YELLOW_DOT = Color(0xFFFFCC00)

/**
 * Show backup creation failures as a settings row.
 */
@Composable
fun BackupCreateErrorRow(
  showCouldNotComplete: Boolean,
  showBackupFailed: Boolean,
  onLearnMoreClick: () -> Unit = {}
) {
  if (showBackupFailed) {
    val inlineContentMap = mapOf(
      "yellow_bullet" to InlineTextContent(
        Placeholder(20.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
      ) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .background(color = YELLOW_DOT, shape = CircleShape)
        )
      }
    )

    BackupAlertText(
      text = buildAnnotatedString {
        appendInlineContent("yellow_bullet")
        append(" ")
        append(stringResource(R.string.BackupStatusRow__your_last_backup_latest_version))
        append(" ")
        withLink(
          LinkAnnotation.Clickable(
            stringResource(R.string.BackupStatusRow__learn_more),
            styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
          ) {
            onLearnMoreClick()
          }
        ) {
          append(stringResource(R.string.BackupStatusRow__learn_more))
        }
      },
      inlineContent = inlineContentMap
    )
  } else if (showCouldNotComplete) {
    val inlineContentMap = mapOf(
      "yellow_bullet" to InlineTextContent(
        Placeholder(20.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
      ) {
        Box(
          modifier = Modifier
            .size(12.dp)
            .background(color = YELLOW_DOT, shape = CircleShape)
        )
      }
    )

    BackupAlertText(
      text = buildAnnotatedString {
        appendInlineContent("yellow_bullet")
        append(" ")
        append(stringResource(R.string.BackupStatusRow__your_last_backup))
      },
      inlineContent = inlineContentMap
    )
  }
}

@Composable
private fun BackupAlertText(text: AnnotatedString, inlineContent: Map<String, InlineTextContent>) {
  Text(
    text = text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter)),
    inlineContent = inlineContent
  )
}

@SignalPreview
@Composable
fun BackupStatusRowCouldNotCompleteBackupPreview() {
  Previews.Preview {
    BackupCreateErrorRow(showCouldNotComplete = true, showBackupFailed = false)
  }
}

@SignalPreview
@Composable
fun BackupStatusRowBackupFailedPreview() {
  Previews.Preview {
    BackupCreateErrorRow(showCouldNotComplete = false, showBackupFailed = true)
  }
}

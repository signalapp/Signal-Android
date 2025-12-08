/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import kotlin.time.Duration.Companion.days
import org.signal.core.ui.R as CoreUiR

private val YELLOW_DOT = Color(0xFFFFCC00)

/**
 * Show backup creation failures as a settings row.
 */
@Composable
fun BackupCreateErrorRow(
  error: BackupValues.BackupCreationError,
  lastMessageCutoffTime: Long = 0,
  onLearnMoreClick: () -> Unit = {}
) {
  val context = LocalContext.current
  val locale = Locale.getDefault()

  when (error) {
    BackupValues.BackupCreationError.TRANSIENT -> {
      BackupAlertText {
        append(stringResource(R.string.BackupStatusRow__your_last_backup))
      }
    }

    BackupValues.BackupCreationError.VALIDATION -> {
      BackupAlertText {
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
      }
    }

    BackupValues.BackupCreationError.BACKUP_FILE_TOO_LARGE -> {
      BackupAlertText {
        if (lastMessageCutoffTime > 0) {
          append(stringResource(R.string.BackupStatusRow__not_backing_up_old_messages, DateUtils.getDayPrecisionTimeString(context, locale, lastMessageCutoffTime)))
        } else {
          append(stringResource(R.string.BackupStatusRow__backup_file_too_large))
        }
      }
    }

    BackupValues.BackupCreationError.NOT_ENOUGH_DISK_SPACE -> {
      BackupAlertText {
        append(stringResource(R.string.BackupStatusRow__not_enough_disk_space, DateUtils.getDayPrecisionTimeString(context, locale, lastMessageCutoffTime)))
      }
    }
  }
}

@Composable
private fun BackupAlertText(stringBuilder: @Composable Builder.() -> Unit) {
  Text(
    text = buildAnnotatedString {
      appendInlineContent("yellow_bullet")
      append(" ")
      stringBuilder()
    },
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter)),
    inlineContent = mapOf(
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
  )
}

@DayNightPreviews
@Composable
fun BackupStatusRowCouldNotCompleteBackupPreview() {
  Previews.Preview {
    Column {
      for (error in BackupValues.BackupCreationError.entries) {
        Text(error.name)
        BackupCreateErrorRow(error = error, onLearnMoreClick = {})
        Spacer(modifier = Modifier.size(8.dp))
      }

      Text(BackupValues.BackupCreationError.BACKUP_FILE_TOO_LARGE.name + " with cutoff duration")
      BackupCreateErrorRow(error = BackupValues.BackupCreationError.BACKUP_FILE_TOO_LARGE, lastMessageCutoffTime = System.currentTimeMillis() - 365.days.inWholeMilliseconds, onLearnMoreClick = {})
      Spacer(modifier = Modifier.size(8.dp))
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

@Composable
fun RecoveryKeyWarningSheetContent(
  clipStage: ClipStage,
  events: (RecoveryKeyWarningSheetEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.horizontalGutters(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.ic_warning_40),
      tint = MaterialTheme.colorScheme.error,
      contentDescription = null,
      modifier = Modifier
        .padding(top = 20.dp, bottom = 16.dp)
        .size(80.dp)
        .background(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape)
        .padding(20.dp)
    )

    Text(
      text = stringResource(R.string.RecoveryKeyWarningSheetContent__do_not_share_your_recovery_key),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
          append(stringResource(R.string.RecoveryKeyWarningSheetContent__signal_will_never_message_you))
        }

        append(" ")
        append(stringResource(R.string.RecoveryKeyWarningSheetContent__for_your_recovery_key_never_respond))

        if (clipStage == ClipStage.PASTE) {
          append(" ")
          withLink(
            link = LinkAnnotation.Clickable(
              tag = "learn-more",
              styles = TextLinkStyles(
                style = SpanStyle(
                  color = MaterialTheme.colorScheme.primary
                )
              )
            ) {
              events(RecoveryKeyWarningSheetEvent.LearnMoreClick)
            }
          ) {
            append(stringResource(R.string.RecoveryKeyWarningSheetContent__learn_more_period))
          }
        }
      },
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 75.dp)
    )

    when (clipStage) {
      ClipStage.COPY -> CopyActionButtons(events = events)
      ClipStage.PASTE -> PasteActionButtons(events = events)
    }

    Spacer(modifier = Modifier.size(16.dp))
  }
}

@Composable
fun CopyActionButtons(events: (RecoveryKeyWarningSheetEvent) -> Unit) {
  Buttons.LargeTonal(onClick = {
    events(RecoveryKeyWarningSheetEvent.GotItClick)
  }) {
    Text(text = stringResource(R.string.RecoveryKeyWarningSheetContent__got_it))
  }

  TextButton(onClick = {
    events(RecoveryKeyWarningSheetEvent.LearnMoreClick)
  }) {
    Text(text = stringResource(R.string.RecoveryKeyWarningSheetContent__learn_more))
  }
}

@Composable
fun PasteActionButtons(events: (RecoveryKeyWarningSheetEvent) -> Unit) {
  Buttons.LargeTonal(onClick = {
    events(RecoveryKeyWarningSheetEvent.DoNotShareClick)
  }) {
    Text(text = stringResource(R.string.RecoveryKeyWarningSheetContent__do_not_share_key))
  }

  TextButton(
    colors = ButtonDefaults.textButtonColors(
      contentColor = MaterialTheme.colorScheme.error
    ),
    onClick = {
      events(RecoveryKeyWarningSheetEvent.ShareKeyClick)
    }
  ) {
    Text(text = stringResource(R.string.RecoveryKeyWarningSheetContent__share_key))
  }
}

enum class ClipStage {
  COPY,
  PASTE
}

@DayNightPreviews
@Composable
private fun RecoveryKeyWarningSheetContentCopyPreview() {
  Previews.BottomSheetPreview {
    RecoveryKeyWarningSheetContent(
      clipStage = ClipStage.COPY,
      events = {},
      modifier = Modifier.fillMaxSize()
    )
  }
}

@DayNightPreviews
@Composable
private fun RecoveryKeyWarningSheetContentPastePreview() {
  Previews.BottomSheetPreview {
    RecoveryKeyWarningSheetContent(
      clipStage = ClipStage.PASTE,
      events = {},
      modifier = Modifier.fillMaxSize()
    )
  }
}

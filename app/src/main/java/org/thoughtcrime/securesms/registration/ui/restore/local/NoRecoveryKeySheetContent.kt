/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

/**
 * Displayed when the user presses the 'No recovery key?' button on the
 * [EnterLocalBackupKeyScreen].
 */
@Composable
fun NoRecoveryKeySheetContent(
  onSkipAndDontRestoreClick: () -> Unit,
  onLearnMoreClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_key_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .padding(top = 56.dp)
        .size(88.dp)
        .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
        .padding(20.dp)
    )

    Text(
      text = "No recovery key?",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(top = 16.dp)
    )

    Text(
      text = "Backups can’t be recovered without their 64-character recovery key. If you’ve lost your recovery key Signal can’t help restore your backup.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 12.dp)
    )

    Text(
      text = "If you have your old device you can view your recovery key in Settings > Backups.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 24.dp, bottom = 56.dp)
    )

    Row(
      modifier = Modifier
        .horizontalGutters()
        .padding(bottom = 24.dp)
    ) {
      TextButton(
        onClick = onLearnMoreClick
      ) {
        Text(text = "Learn more")
      }

      Spacer(modifier = Modifier.weight(1f))

      TextButton(
        onClick = onSkipAndDontRestoreClick
      ) {
        Text(text = "Skip and don't restore")
      }
    }
  }
}

@DayNightPreviews
@Composable
fun NoRecoveryKeySheetContentPreview() {
  Previews.BottomSheetContentPreview {
    NoRecoveryKeySheetContent(
      onSkipAndDontRestoreClick = {},
      onLearnMoreClick = {}
    )
  }
}

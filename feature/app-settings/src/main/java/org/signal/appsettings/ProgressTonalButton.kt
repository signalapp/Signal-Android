/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons

/**
 * A primary-container tonal button that swaps its label for a spinner while [inProgress], keeping the label's size so
 * the button doesn't jump. It can't be clicked while in progress.
 */
@Composable
internal fun ProgressTonalButton(
  text: String,
  onClick: () -> Unit,
  inProgress: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Buttons.LargeTonal(
    onClick = onClick,
    enabled = enabled && !inProgress,
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    modifier = modifier
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = text,
        modifier = Modifier.alpha(if (inProgress) 0f else 1f)
      )

      if (inProgress) {
        CircularProgressIndicator(
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.thoughtcrime.securesms.R

/**
 * Explanation screen that details how the user's pin is utilized with backups,
 * and how long they should make their pin.
 */
@Composable
fun MessageBackupsPinEducationScreen(
  onNavigationClick: () -> Unit,
  onGeneratePinClick: () -> Unit,
  onUseCurrentPinClick: () -> Unit,
  recommendedPinSize: Int
) {
  Scaffolds.Settings(
    title = "Backup type", // TODO [message-backups] Finalized copy
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
    ) {
      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        item {
          Image(
            painter = painterResource(id = R.drawable.ic_signal_logo_large), // TODO [message-backups] Finalized image
            contentDescription = null,
            modifier = Modifier
              .padding(top = 48.dp)
              .size(88.dp)
          )
        }

        item {
          Text(
            text = "PINs protect your backup", // TODO [message-backups] Finalized copy
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          Text(
            text = "Your Signal PIN lets you restore your backup when you re-install Signal. For increased security, we recommend updating to a new $recommendedPinSize-digit PIN.", // TODO [message-backups] Finalized copy
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          Text(
            text = "If you forget your PIN, you will not be able to restore your backup. You can change your PIN at any time in settings.", // TODO [message-backups] Finalized copy
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
          )
        }
      }

      Buttons.LargePrimary(
        onClick = onUseCurrentPinClick,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "Use current Signal PIN" // TODO [message-backups] Finalized copy
        )
      }

      TextButton(
        onClick = onGeneratePinClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
      ) {
        Text(
          text = "Generate a new $recommendedPinSize-digit PIN" // TODO [message-backups] Finalized copy
        )
      }
    }
  }
}

@Preview
@Composable
private fun MessageBackupsPinScreenPreview() {
  Previews.Preview {
    MessageBackupsPinEducationScreen(
      onNavigationClick = {},
      onGeneratePinClick = {},
      onUseCurrentPinClick = {},
      recommendedPinSize = 16
    )
  }
}

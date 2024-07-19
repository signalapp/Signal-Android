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
import androidx.compose.ui.res.stringResource
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
  onCreatePinClick: () -> Unit,
  onUseCurrentPinClick: () -> Unit,
  recommendedPinSize: Int
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.RemoteBackupsSettingsFragment__signal_backups),
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
            text = stringResource(id = R.string.MessageBackupsPinEducationScreen__pins_protect_your_backup),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          Text(
            text = stringResource(id = R.string.MessageBackupsPinEducationScreen__your_signal_pin_lets_you),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        item {
          Text(
            text = stringResource(id = R.string.MessageBackupsPinEducationScreen__if_you_forget_your_pin),
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
          text = stringResource(id = R.string.MessageBackupsPinEducationScreen__use_current_signal_pin)
        )
      }

      TextButton(
        onClick = onCreatePinClick,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
      ) {
        Text(
          text = stringResource(id = R.string.MessageBackupsPinEducationScreen__create_new_pin)
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
      onCreatePinClick = {},
      onUseCurrentPinClick = {},
      recommendedPinSize = 16
    )
  }
}

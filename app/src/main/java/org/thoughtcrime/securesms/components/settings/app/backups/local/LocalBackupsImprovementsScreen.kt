/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * Screen explaining the improvements made to the on-device backups experience.
 */
@Composable
fun LocalBackupsImprovementsScreen(
  onNavigationClick: () -> Unit = {},
  onContinueClick: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = "",
    navigationIcon = ImageVector.vectorResource(CoreUiR.drawable.symbol_x_24),
    onNavigationClick = onNavigationClick
  ) {
    Column(
      Modifier
        .padding(it)
        .horizontalGutters()
        .fillMaxSize()
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        item {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_folder_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .padding(top = 24.dp, bottom = 16.dp)
              .size(80.dp)
              .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
              .padding(16.dp)
          )
        }

        item {
          Text(
            text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__improvements_to_on_device_backups),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
          )
        }

        item {
          Text(
            text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__your_on_device_backup_will_be_upgraded),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 36.dp)
          )
        }

        item {
          FeatureRow(
            imageVector = ImageVector.vectorResource(CoreUiR.drawable.symbol_backup_24),
            text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__backups_now_save_faster)
          )
        }

        item {
          FeatureRow(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_folder_24),
            text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__your_backup_will_be_saved_as_a_folder)
          )
        }

        item {
          FeatureRow(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_key_24),
            text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__all_backups_remain_end_to_end_encrypted)
          )
        }
      }

      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onContinueClick
        ) {
          Text(text = stringResource(R.string.OnDeviceBackupsImprovementsScreen__continue))
        }
      }
    }
  }
}

@Composable
private fun FeatureRow(
  imageVector: ImageVector,
  text: String
) {
  Row(
    modifier = Modifier.padding(bottom = 16.dp)
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(start = 16.dp)
        .widthIn(max = 217.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun LocalBackupsImprovementsScreenPreview() {
  Previews.Preview {
    LocalBackupsImprovementsScreen()
  }
}

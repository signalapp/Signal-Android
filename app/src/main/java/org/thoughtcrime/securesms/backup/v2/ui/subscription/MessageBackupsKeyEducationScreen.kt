/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * Screen detailing how a backups key is used to restore a backup
 */
@Composable
fun MessageBackupsKeyEducationScreen(
  onNavigationClick: () -> Unit = {},
  onNextClick: () -> Unit = {}
) {
  val scrollState = rememberScrollState()

  Scaffolds.Settings(
    title = "",
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) {
    Column(
      modifier = Modifier
        .padding(it)
        .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
        .fillMaxSize()
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(R.drawable.image_signal_backups_key),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 24.dp)
          .size(80.dp)
      )

      Text(
        text = stringResource(R.string.MessageBackupsKeyEducationScreen__your_backup_key),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp)
      )

      InfoRow(
        R.drawable.symbol_number_24,
        R.string.MessageBackupsKeyEducationScreen__your_backup_key_is_a
      )

      InfoRow(
        R.drawable.symbol_lock_24,
        R.string.MessageBackupsKeyEducationScreen__store_your_recovery
      )

      InfoRow(
        R.drawable.symbol_error_circle_24,
        R.string.MessageBackupsKeyEducationScreen__if_you_lose_it
      )

      Spacer(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, bottom = 24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onNextClick,
          modifier = Modifier.align(Alignment.Center)
        ) {
          Text(
            text = stringResource(R.string.MessageBackupsKeyEducationScreen__view_recovery_key),
            modifier = Modifier.padding(horizontal = 20.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun InfoRow(@DrawableRes iconId: Int, @StringRes textId: Int) {
  Row(
    verticalAlignment = Alignment.Top,
    modifier = Modifier.padding(top = 24.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(iconId),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant

    )
    Text(
      text = stringResource(textId),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun MessageBackupsKeyEducationScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyEducationScreen()
  }
}

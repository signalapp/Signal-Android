/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice.preparedevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.olddevice.QuickTransferOldDeviceState
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import org.signal.core.ui.R as CoreUiR

@Composable
fun PrepareDeviceScreen(
  state: QuickTransferOldDeviceState,
  emitter: (PrepareDeviceScreenEvents) -> Unit
) {
  Scaffolds.Default(
    onNavigationClick = { emitter(PrepareDeviceScreenEvents.NavigateBack) },
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24
  ) { contentPadding ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .horizontalGutters()
    ) {
      Text(
        text = stringResource(R.string.PrepareDevice_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(R.string.PrepareDevice_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.weight(1f))

      Image(
        painter = painterResource(R.drawable.illustration_prepare_backup),
        contentDescription = null,
        modifier = Modifier.width(120.dp)
      )

      Spacer(modifier = Modifier.height(24.dp))

      if (state.lastBackupTimestamp > 0) {
        val context = LocalContext.current

        val dateString = DateUtils.getDateTimeString(context, Locale.getDefault(), state.lastBackupTimestamp)

        Text(
          text = stringResource(R.string.PrepareDevice_last_backup_description, dateString),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = { emitter(PrepareDeviceScreenEvents.BackUpNow) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = stringResource(R.string.PrepareDevice_back_up_now))
      }

      TextButton(
        onClick = { emitter(PrepareDeviceScreenEvents.SkipAndContinue) },
        modifier = Modifier.padding(vertical = 8.dp)
      ) {
        Text(
          text = stringResource(R.string.PrepareDevice_skip_and_continue),
          color = MaterialTheme.colorScheme.primary
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@DayNightPreviews
@Composable
private fun PrepareDeviceScreenPreview() {
  Previews.Preview {
    PrepareDeviceScreen(
      state = QuickTransferOldDeviceState(
        reRegisterUri = "",
        lastBackupTimestamp = System.currentTimeMillis() - 86400000 // Yesterday
      ),
      emitter = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PrepareDeviceScreenNoBackupPreview() {
  Previews.Preview {
    PrepareDeviceScreen(
      state = QuickTransferOldDeviceState(reRegisterUri = ""),
      emitter = {}
    )
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold

@Composable
fun DeviceTransferInstructionsScreen(
  state: DeviceTransferInstructionsState,
  onEvent: (DeviceTransferInstructionsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
          painter = painterResource(R.drawable.symbol_transfer_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(R.string.DeviceTransferInstructions__transfer_your_account),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(R.string.DeviceTransferInstructions__to_transfer_open_signal_on_your_old_android_device),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    },
    footer = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(DeviceTransferInstructionsScreenEvents.ContinueClicked) },
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
        ) {
          Text(stringResource(R.string.DeviceTransferInstructions__continue))
        }
      }
    }
  )
}

@AllDevicePreviews
@Composable
private fun DeviceTransferInstructionsScreenPreview() {
  Previews.Preview {
    DeviceTransferInstructionsScreen(
      state = DeviceTransferInstructionsState(),
      onEvent = {}
    )
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import org.thoughtcrime.securesms.R

/**
 * Full-screen blocking screen shown when we've detected that the local device clock is too far out of sync with the
 * server's clock (see [ClockSkewDetector]).
 */
@Composable
fun ClockSkewScreen(
  state: ClockSkewState,
  onEvent: (ClockSkewScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(modifier = modifier) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .displayCutoutPadding()
        .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
      Text(
        text = stringResource(R.string.ClockSkewActivity__title),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = maxHeight)
            .verticalScroll(rememberScrollState())
        ) {
          Spacer(modifier = Modifier.height(64.dp))
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(150.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
          ) {
            Icon(
              painter = painterResource(R.drawable.symbol_recent_24),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(96.dp)
            )
          }

          Spacer(modifier = Modifier.height(64.dp))

          Text(
            text = stringResource(R.string.ClockSkewActivity__date_inaccurate_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = stringResource(R.string.ClockSkewActivity__time_prefix, state.deviceDateTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      Buttons.LargePrimary(
        onClick = { onEvent(ClockSkewScreenEvent.AdjustDateSelected) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = stringResource(R.string.ClockSkewActivity__adjust_date_button))
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun ClockSkewScreenPreview() {
  Previews.Preview {
    ClockSkewScreen(
      state = ClockSkewState(deviceDateTime = "12/17/25, 11:26 PM\n(Greenwich Mean Time)"),
      onEvent = {}
    )
  }
}

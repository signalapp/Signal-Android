/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews

/**
 * Screen shown when the user's account is locked due to too many failed PIN attempts
 * and there's no SVR data available to recover.
 */
@Composable
fun AccountLockedScreen(
  state: AccountLockedState,
  onEvent: (AccountLockedScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(49.dp))

      Text(
        text = "Account locked",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Your account has been locked to protect your privacy and security. After ${state.daysRemaining} days of inactivity in your account you'll be able to re-register this phone number without needing your PIN. All content will be deleted.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Button(
        onClick = { onEvent(AccountLockedScreenEvents.Next) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Next")
      }

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedButton(
        onClick = { onEvent(AccountLockedScreenEvents.LearnMore) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Learn More")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@DayNightPreviews
@Composable
private fun AccountLockedScreenPreview() {
  Previews.Preview {
    AccountLockedScreen(
      state = AccountLockedState(daysRemaining = 7),
      onEvent = {}
    )
  }
}

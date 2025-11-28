/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews

@Composable
fun MainScreen(
  state: MainScreenState,
  onEvent: (MainScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Registration Sample App",
      style = MaterialTheme.typography.headlineMedium
    )

    Text(
      text = "Test the registration flow",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(top = 8.dp)
    )

    Button(
      onClick = { onEvent(MainScreenEvents.LaunchRegistration) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 48.dp)
    ) {
      Text("Start Registration")
    }

    if (state.existingRegistrationState != null) {
      RegistrationInfo(state.existingRegistrationState)
    }
  }
}

@Composable
private fun RegistrationInfo(data: MainScreenState.ExistingRegistrationState) {
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  Previews.Preview {
    MainScreen(
      state = MainScreenState(),
      onEvent = {}
    )
  }
}

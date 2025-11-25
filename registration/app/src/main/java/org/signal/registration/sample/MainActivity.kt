/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.RegistrationActivity

/**
 * Sample app activity that launches the registration flow for testing.
 */
class MainActivity : ComponentActivity() {

  private val registrationLauncher = registerForActivityResult(
    RegistrationActivity.RegistrationContract()
  ) { success ->
    registrationComplete = success
  }

  private var registrationComplete by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          if (registrationComplete) {
            RegistrationCompleteScreen(
              onStartOver = {
                registrationComplete = false
              }
            )
          } else {
            MainScreen(
              onLaunchRegistration = {
                registrationLauncher.launch(Unit)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MainScreen(
  onLaunchRegistration: () -> Unit,
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
      onClick = onLaunchRegistration,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 48.dp)
    ) {
      Text("Start Registration")
    }
  }
}

@Composable
private fun RegistrationCompleteScreen(
  onStartOver: () -> Unit,
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
      text = "Registration Complete!",
      style = MaterialTheme.typography.headlineMedium
    )

    Button(
      onClick = onStartOver,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 48.dp)
    ) {
      Text("Start Over")
    }
  }
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens

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
fun RegistrationCompleteScreen(
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

@Preview(showBackground = true)
@Composable
private fun RegistrationCompleteScreenPreview() {
  Previews.Preview {
    RegistrationCompleteScreen(onStartOver = {})
  }
}

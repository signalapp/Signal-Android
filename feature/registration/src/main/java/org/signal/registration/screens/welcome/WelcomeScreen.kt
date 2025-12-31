/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.registration.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.registration.test.TestTags

/**
 * Welcome screen for the registration flow.
 * This is the initial screen users see when starting the registration process.
 */
@Composable
fun WelcomeScreen(
  onEvent: (WelcomeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var showBottomSheet by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Welcome to Signal",
      style = MaterialTheme.typography.headlineLarge,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(
      onClick = { onEvent(WelcomeScreenEvents.Continue) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.WELCOME_GET_STARTED_BUTTON)
    ) {
      Text("Get Started")
    }
    OutlinedButton(
      onClick = { showBottomSheet = true },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON)
    ) {
      Text("Restore or transfer")
    }
  }

  if (showBottomSheet) {
    RestoreOrTransferBottomSheet(
      onEvent = {
        showBottomSheet = false
        onEvent(it)
      },
      onDismiss = { showBottomSheet = false }
    )
  }
}

/**
 * Bottom sheet for restore or transfer options.
 */
@Composable
private fun RestoreOrTransferBottomSheet(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    RestoreOrTransferBottomSheetContent(
      sheetState = sheetState,
      onEvent = onEvent,
      scope = scope
    )
  }
}

/**
 * Bottom sheet content for restore or transfer options (needs to be separate for preview).
 */
@Composable
private fun RestoreOrTransferBottomSheetContent(
  sheetState: SheetState,
  scope: CoroutineScope,
  onEvent: (WelcomeScreenEvents) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Button(
      onClick = {
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.HasOldPhone)
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON)
    ) {
      Text("I have my old phone")
    }

    Button(
      onClick = {
        onEvent(WelcomeScreenEvents.DoesNotHaveOldPhone)
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.DoesNotHaveOldPhone)
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON)
    ) {
      Text("I don't have my old phone")
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@DayNightPreviews
@Composable
private fun WelcomeScreenPreview() {
  Previews.Preview {
    WelcomeScreen(onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun RestoreOrTransferBottomSheetPreview() {
  Previews.BottomSheetPreview(forceRtl = true) {
    RestoreOrTransferBottomSheetContent(
      sheetState = rememberModalBottomSheetState(),
      scope = rememberCoroutineScope(),
      onEvent = {}
    )
  }
}

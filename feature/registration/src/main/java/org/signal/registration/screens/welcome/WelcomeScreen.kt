/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.registration.screens.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.R
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
      .testTag(TestTags.WELCOME_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Image(
      painter = painterResource(R.drawable.welcome),
      contentDescription = null,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(16.dp),
      contentScale = ContentScale.Fit
    )

    Text(
      text = stringResource(R.string.RegistrationActivity_take_privacy_with_you_be_yourself_in_every_message),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .padding(horizontal = 32.dp)
        .testTag(TestTags.WELCOME_HEADLINE)
    )

    Spacer(modifier = Modifier.height(40.dp))

    TextButton(
      onClick = { /* Terms & Privacy link */ },
      colors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
      )
    ) {
      Text(
        text = stringResource(R.string.RegistrationActivity_terms_and_privacy),
        textAlign = TextAlign.Center
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Buttons.LargeTonal(
      onClick = { onEvent(WelcomeScreenEvents.Continue) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .testTag(TestTags.WELCOME_GET_STARTED_BUTTON)
    ) {
      Text(stringResource(R.string.RegistrationActivity_continue))
    }

    Spacer(modifier = Modifier.height(17.dp))

    Buttons.LargeTonal(
      onClick = { showBottomSheet = true },
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = SignalTheme.colors.colorSurface2
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .testTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON)
    ) {
      Text(stringResource(R.string.registration_activity__restore_or_transfer))
    }

    Spacer(modifier = Modifier.height(48.dp))
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
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 54.dp)
  ) {
    Spacer(modifier = Modifier.size(26.dp))

    RestoreActionRow(
      icon = SignalIcons.QrCode.painter,
      title = stringResource(R.string.WelcomeFragment_restore_action_i_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_scan_qr),
      modifier = Modifier.testTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON),
      onRowClick = {
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.HasOldPhone)
        }
      }
    )

    RestoreActionRow(
      icon = painterResource(R.drawable.symbol_no_phone_44),
      title = stringResource(R.string.WelcomeFragment_restore_action_i_dont_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_reinstalling),
      modifier = Modifier.testTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON),
      onRowClick = {
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.DoesNotHaveOldPhone)
        }
      }
    )
  }
}

@Composable
private fun RestoreActionRow(
  icon: Painter,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onRowClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .horizontalGutters()
      .padding(vertical = 8.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.background)
      .clickable(enabled = true, onClick = onRowClick)
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Icon(
      painter = icon,
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier.size(44.dp)
    )

    Column(
      modifier = Modifier.padding(start = 16.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun WelcomeScreenPreview() {
  Previews.Preview {
    WelcomeScreen(onEvent = {})
  }
}

@AllDevicePreviews
@Composable
private fun RestoreOrTransferBottomSheetPreview() {
  Previews.BottomSheetPreview {
    RestoreOrTransferBottomSheetContent(
      sheetState = rememberModalBottomSheetState(),
      scope = rememberCoroutineScope(),
      onEvent = {}
    )
  }
}

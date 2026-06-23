/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.QrCode
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper

/**
 * Screen to display QR code for restoring from an old device.
 * The old device scans this QR code to initiate the transfer.
 */
@Composable
fun QuickRestoreQrScreen(
  state: QuickRestoreQrState,
  onEvent: (QuickRestoreQrEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent, modifier)
    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent, modifier)
  }

  StateDialogs(state = state, onEvent = onEvent)
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: QuickRestoreQrState,
  onEvent: (QuickRestoreQrEvents) -> Unit,
  modifier: Modifier
) {
  val scrollState = rememberScrollState()
  OnePaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Heading()
        Spacer(modifier = Modifier.height(32.dp))
        QrCodePane(state = state, onEvent = onEvent)
        Spacer(modifier = Modifier.height(32.dp))
        Instructions()
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = scrollState.canScrollForward
      ) {
        CancelFooter(onEvent)
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: QuickRestoreQrState,
  onEvent: (QuickRestoreQrEvents) -> Unit,
  modifier: Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Heading()
        Spacer(modifier = Modifier.height(32.dp))
        Instructions()
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        QrCodePane(state = state, onEvent = onEvent)
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
      ) {
        CancelFooter(onEvent)
      }
    }
  )
}

@Composable
private fun Heading() {
  Text(
    text = stringResource(R.string.QuickRestoreQRScreen__scan),
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )
}

@Composable
private fun QrCodePane(
  state: QuickRestoreQrState,
  onEvent: (QuickRestoreQrEvents) -> Unit
) {
  Box(
    modifier = Modifier
      .widthIn(max = 280.dp)
      .aspectRatio(1f)
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      AnimatedContent(
        targetState = state.qrState,
        contentKey = { it::class },
        label = "qr-code-state"
      ) { qrState ->
        when (qrState) {
          is QrState.Loaded -> {
            QrCode(
              data = qrState.qrCodeData,
              foregroundColor = Color(0xFF2449C0),
              modifier = Modifier.fillMaxSize()
            )
          }

          QrState.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
          }

          QrState.Scanned -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = stringResource(R.string.QuickRestoreQRScreen__scanned),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(8.dp))

              Button(onClick = { onEvent(QuickRestoreQrEvents.RetryQrCode) }) {
                Text(stringResource(R.string.QuickRestoreQRScreen__retry))
              }
            }
          }

          QrState.Failed -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = stringResource(R.string.QuickRestoreQRScreen__failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
              )

              Spacer(modifier = Modifier.height(8.dp))

              Button(onClick = { onEvent(QuickRestoreQrEvents.RetryQrCode) }) {
                Text(stringResource(R.string.QuickRestoreQRScreen__retry))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Instructions() {
  Column(
    modifier = Modifier.widthIn(max = 320.dp)
  ) {
    InstructionRow(
      vector = SignalIcons.Phone.imageVector,
      instruction = stringResource(R.string.QuickRestoreQRScreen__step_1)
    )

    InstructionRow(
      vector = SignalIcons.Camera.imageVector,
      instruction = stringResource(R.string.QuickRestoreQRScreen__step_2)
    )

    InstructionRow(
      vector = SignalIcons.QrCode.imageVector,
      instruction = stringResource(R.string.QuickRestoreQRScreen__step_3)
    )
  }
}

@Composable
private fun CancelFooter(onEvent: (QuickRestoreQrEvents) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 16.dp),
    horizontalArrangement = Arrangement.Center
  ) {
    TextButton(
      onClick = { onEvent(QuickRestoreQrEvents.Cancel) }
    ) {
      Text(stringResource(android.R.string.cancel))
    }
  }
}

@Composable
private fun StateDialogs(
  state: QuickRestoreQrState,
  onEvent: (QuickRestoreQrEvents) -> Unit
) {
  if (state.isRegistering) {
    Dialogs.IndeterminateProgressDialog(
      stringResource(R.string.QuickRestoreQRScreen__reregister)
    )
  }

  if (state.showRegistrationError) {
    Dialogs.SimpleMessageDialog(
      message = state.errorMessage ?: stringResource(R.string.QuickRestoreQRScreen__error),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(QuickRestoreQrEvents.DismissError) }
    )
  }
}

@Composable
private fun InstructionRow(
  vector: ImageVector,
  instruction: String
) {
  Row(
    modifier = Modifier.padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = vector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@AllDevicePreviews
@Composable
private fun QuickRestoreQrScreenLoadingPreview() {
  Previews.Preview {
    QuickRestoreQrScreen(
      state = QuickRestoreQrState(qrState = QrState.Loading),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun QuickRestoreQrScreenLoadedPreview() {
  Previews.Preview {
    QuickRestoreQrScreen(
      state = QuickRestoreQrState(
        qrState = QrState.Loaded(QrCodeData.forData("sgnl://rereg?uuid=test&pub_key=test", false))
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun QuickRestoreQrScreenFailedPreview() {
  Previews.Preview {
    QuickRestoreQrScreen(
      state = QuickRestoreQrState(qrState = QrState.Failed),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun QuickRestoreQrScreenRegisteringPreview() {
  Previews.Preview {
    QuickRestoreQrScreen(
      state = QuickRestoreQrState(
        qrState = QrState.Scanned,
        isRegistering = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun QuickRestoreQrScreenRegisteringFailedPreview() {
  Previews.Preview {
    QuickRestoreQrScreen(
      state = QuickRestoreQrState(
        qrState = QrState.Scanned,
        showRegistrationError = true
      ),
      onEvent = {}
    )
  }
}

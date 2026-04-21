/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.camera.CameraCaptureMode
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenState
import org.signal.camera.CameraScreenViewModel
import org.signal.core.ui.compose.Cutout
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * Scanner screen for verifying user identities. This is meant to be utilized with an instance of
 * [CameraScreenViewModel], which the parent component owns. There is a field on the ViewModel through
 * which you can recieve via [qrCodeDetected]
 */
@Composable
fun VerifyScanScreen(
  state: CameraScreenState,
  emitter: (CameraScreenEvents) -> Unit
) {
  Column {
    CameraScreen(
      state = state,
      emitter = emitter,
      roundCorners = false,
      enableQrScanning = true,
      captureMode = CameraCaptureMode.ImageOnly,
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      Cutout(
        cutoutShape = RoundedCornerShape(18.dp),
        cutoutPadding = PaddingValues(64.dp),
        modifier = Modifier.fillMaxSize()
      )

      Image(
        painter = painterResource(R.drawable.ic_camera_outline),
        contentDescription = null,
        modifier = Modifier.align(Alignment.Center)
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.heightIn(min = 60.dp)
    ) {
      Text(
        text = stringResource(R.string.verify_scan_fragment__scan_the_qr_code_on_your_contact),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun VerifyScanScreenPreview() {
  Previews.Preview {
    VerifyScanScreen(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

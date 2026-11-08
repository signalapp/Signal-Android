/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.signal.camera.CameraCaptureMode
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenState
import org.thoughtcrime.securesms.qr.QrCrosshair

/** The camera viewfinder for scanning username QR codes, framed by a crosshair. */
@Composable
fun UsernameQrCameraPreview(
  cameraState: CameraScreenState,
  cameraEmitter: (CameraScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  CameraScreen(
    state = cameraState,
    emitter = cameraEmitter,
    enableQrScanning = true,
    captureMode = CameraCaptureMode.ImageOnly,
    roundCorners = false,
    fillViewport = true,
    modifier = modifier
  ) {
    QrCrosshair(modifier = Modifier.fillMaxSize())
  }
}

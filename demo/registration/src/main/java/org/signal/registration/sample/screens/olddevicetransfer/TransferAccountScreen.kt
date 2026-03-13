/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.olddevicetransfer

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.qr.QrScannerView

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TransferAccountScreen(
  state: TransferAccountState,
  onEvent: (TransferAccountEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  when (state) {
    TransferAccountState.Scanning -> {
      val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

      if (cameraPermission.status.isGranted) {
        QrScannerContent(
          onQrScanned = { onEvent(TransferAccountEvent.QrCodeScanned(it)) },
          onBack = { onEvent(TransferAccountEvent.Back) },
          modifier = modifier
        )
      } else {
        CameraPermissionContent(
          onRequestPermission = { cameraPermission.launchPermissionRequest() },
          onBack = { onEvent(TransferAccountEvent.Back) },
          modifier = modifier
        )
      }
    }
    TransferAccountState.Sending -> SendingContent(modifier = modifier)
    TransferAccountState.Success -> SuccessContent(
      onBack = { onEvent(TransferAccountEvent.Back) },
      modifier = modifier
    )
    is TransferAccountState.Error -> ErrorContent(
      message = state.message,
      onRetry = { onEvent(TransferAccountEvent.Retry) },
      onBack = { onEvent(TransferAccountEvent.Back) },
      modifier = modifier
    )
  }
}

@Composable
private fun QrScannerContent(
  onQrScanned: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var disposable = remember<Disposable?> { null }

  DisposableEffect(Unit) {
    onDispose { disposable?.dispose() }
  }

  Column(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      AndroidView(
        factory = { context ->
          QrScannerView(context).apply {
            start(lifecycleOwner)
            disposable = qrData.subscribe { data ->
              if (data.startsWith("sgnl://rereg")) {
                onQrScanned(data)
              }
            }
          }
        },
        modifier = Modifier.fillMaxSize()
      )

      Text(
        text = "Scan the QR code on the new device",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(24.dp)
      )
    }

    OutlinedButton(
      onClick = onBack,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Text("Cancel")
    }
  }
}

@Composable
private fun CameraPermissionContent(
  onRequestPermission: () -> Unit,
  onBack: () -> Unit,
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
      text = "Camera Permission Required",
      style = MaterialTheme.typography.headlineMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Camera access is needed to scan the QR code on the new device.",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onRequestPermission) {
      Text("Grant Camera Permission")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = onBack) {
      Text("Cancel")
    }
  }
}

@Composable
private fun SendingContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator(modifier = Modifier.size(48.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Sending account data...",
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@Composable
private fun SuccessContent(
  onBack: () -> Unit,
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
      text = "Transfer Sent",
      style = MaterialTheme.typography.headlineMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Account data has been sent to the new device.",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onBack) {
      Text("Done")
    }
  }
}

@Composable
private fun ErrorContent(
  message: String,
  onRetry: () -> Unit,
  onBack: () -> Unit,
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
      text = "Transfer Failed",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onRetry) {
      Text("Try Again")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = onBack) {
      Text("Cancel")
    }
  }
}

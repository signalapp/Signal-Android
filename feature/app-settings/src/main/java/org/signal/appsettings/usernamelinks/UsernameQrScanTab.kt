/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object UsernameQrScanTabTestTags {
  const val BUTTON_ALLOW_CAMERA = "button-allow-camera"
  const val BUTTON_OPEN_GALLERY = "button-open-gallery"
}

/**
 * The camera, or a prompt to allow it, with a button for picking an image of a QR code instead.
 *
 * @param cameraPreview the camera viewfinder, drawn to fill the modifier it's handed.
 */
@Composable
fun UsernameQrScanTab(
  hasCameraPermission: Boolean,
  onAllowCameraClicked: () -> Unit,
  onOpenGalleryClicked: () -> Unit,
  modifier: Modifier = Modifier,
  cameraPreview: @Composable (Modifier) -> Unit = {}
) {
  Column(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(Color.Black)
    ) {
      if (hasCameraPermission) {
        cameraPreview(Modifier.fillMaxSize())
      } else {
        // Bottom padding keeps the prompt clear of the gallery button.
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 104.dp)
        ) {
          Text(
            text = stringResource(R.string.UsernameLinkSettingsScreen__allow_camera_to_scan),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
          )

          Buttons.MediumTonal(
            colors = ButtonDefaults.filledTonalButtonColors(),
            onClick = onAllowCameraClicked,
            modifier = Modifier
              .padding(top = 16.dp)
              .testTag(UsernameQrScanTabTestTags.BUTTON_ALLOW_CAMERA)
          ) {
            Text(text = stringResource(R.string.UsernameLinkSettingsScreen__allow_access))
          }
        }
      }

      FloatingActionButton(
        shape = CircleShape,
        containerColor = SignalTheme.colors.colorSurface1,
        onClick = onOpenGalleryClicked,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 24.dp)
          .testTag(UsernameQrScanTabTestTags.BUTTON_OPEN_GALLERY)
      ) {
        Icon(
          imageVector = SignalIcons.Album.imageVector,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface
        )
      }
    }

    Text(
      text = stringResource(R.string.UsernameLinkSettings_qr_scan_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    )
  }
}

/** Explains why a scanned or imported QR code couldn't be turned into a chat. */
@Composable
fun QrScanProblemDialog(problem: QrScanProblem, onDismiss: () -> Unit) {
  val title = when (problem) {
    QrScanProblem.QrNotFound -> stringResource(R.string.UsernameLinkSettings_qr_code_not_found)
    else -> null
  }

  val message = when (problem) {
    QrScanProblem.InvalidData -> stringResource(R.string.UsernameLinkSettings_qr_result_invalid)
    QrScanProblem.NetworkError -> stringResource(R.string.UsernameLinkSettings_qr_result_network_error)
    QrScanProblem.QrNotFound -> stringResource(R.string.UsernameLinkSettings_try_scanning_another_image_containing_a_signal_qr_code)
    is QrScanProblem.NotFound -> {
      if (problem.username != null) {
        stringResource(R.string.UsernameLinkSettings_qr_result_not_found, problem.username)
      } else {
        stringResource(R.string.UsernameLinkSettingsScreen__qr_result_not_found_no_username)
      }
    }
  }

  Dialogs.SimpleMessageDialog(
    title = title,
    message = message,
    dismiss = stringResource(android.R.string.ok),
    onDismiss = onDismiss
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun UsernameQrScanTabNoPermissionPreview() {
  UsernameQrScanTab(hasCameraPermission = false, onAllowCameraClicked = {}, onOpenGalleryClicked = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun QrScanProblemDialogPreview() {
  QrScanProblemDialog(problem = QrScanProblem.QrNotFound, onDismiss = {})
}

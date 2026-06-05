package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.signal.camera.CameraCaptureMode
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenState
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult
import org.thoughtcrime.securesms.qr.QrCrosshair
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * A screen that allows you to scan a QR code to link a device
 */
@Composable
fun LinkDeviceQrScanScreen(
  hasPermission: Boolean,
  onRequestPermissions: () -> Unit,
  cameraState: CameraScreenState,
  cameraEmitter: (CameraScreenEvents) -> Unit,
  qrCodeState: LinkDeviceSettingsState.QrCodeState,
  onQrCodeAccepted: () -> Unit,
  onQrCodeDismissed: () -> Unit,
  linkDeviceResult: LinkDeviceResult,
  onLinkDeviceSuccess: () -> Unit,
  onLinkDeviceFailure: () -> Unit,
  navController: NavController?,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  when (qrCodeState) {
    LinkDeviceSettingsState.QrCodeState.NONE -> Unit
    LinkDeviceSettingsState.QrCodeState.VALID_WITH_SYNC -> {
      navController?.safeNavigate(R.id.action_addLinkDeviceFragment_to_linkDeviceSyncBottomSheet)
    }

    LinkDeviceSettingsState.QrCodeState.VALID_WITHOUT_SYNC -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(id = R.string.DeviceProvisioningActivity_link_this_device),
        body = stringResource(id = R.string.AddLinkDeviceFragment__this_device_will_see_your_groups_contacts),
        confirm = stringResource(id = R.string.device_list_fragment__link_new_device),
        onConfirm = onQrCodeAccepted,
        dismiss = stringResource(id = android.R.string.cancel),
        onDismiss = onQrCodeDismissed
      )
    }

    LinkDeviceSettingsState.QrCodeState.INVALID -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(id = R.string.AddLinkDeviceFragment__linking_device_failed),
        body = stringResource(id = R.string.AddLinkDeviceFragment__this_qr_code_not_valid),
        confirm = stringResource(id = R.string.AddLinkDeviceFragment__retry),
        onConfirm = { },
        onDismiss = onQrCodeDismissed
      )
    }
  }

  LaunchedEffect(linkDeviceResult) {
    when (linkDeviceResult) {
      is LinkDeviceResult.Success -> onLinkDeviceSuccess()
      is LinkDeviceResult.NoDevice -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_no_device, onLinkDeviceFailure)
      is LinkDeviceResult.NetworkError -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_network_error, onLinkDeviceFailure)
      is LinkDeviceResult.KeyError -> makeToast(context, R.string.DeviceProvisioningActivity_content_progress_key_error, onLinkDeviceFailure)
      is LinkDeviceResult.LimitExceeded -> makeToast(context, R.string.DeviceProvisioningActivity_sorry_you_have_too_many_devices_linked_already, onLinkDeviceFailure)
      is LinkDeviceResult.BadCode -> makeToast(context, R.string.DeviceActivity_sorry_this_is_not_a_valid_device_link_qr_code, onLinkDeviceFailure)
      is LinkDeviceResult.None -> Unit
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, true)
        .background(Color.Black)
    ) {
      if (hasPermission) {
        CameraScreen(
          state = cameraState,
          emitter = cameraEmitter,
          enableQrScanning = true,
          captureMode = CameraCaptureMode.ImageOnly,
          roundCorners = false,
          fillViewport = true,
          modifier = Modifier.fillMaxSize()
        ) {
          QrCrosshair(modifier = Modifier.fillMaxSize())

          Text(
            text = stringResource(R.string.AddLinkDeviceFragment__scan_the_qr_code),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 24.dp)
              .background(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape)
              .padding(horizontal = 16.dp, vertical = 8.dp)
          )
        }
      } else {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(48.dp)
        ) {
          Text(
            text = stringResource(R.string.CameraXFragment_to_scan_qr_code_allow_camera),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
          )
          Buttons.MediumTonal(
            colors = ButtonDefaults.filledTonalButtonColors(),
            onClick = onRequestPermissions
          ) {
            Text(stringResource(R.string.CameraXFragment_allow_access))
          }
        }
      }
    }
  }
}

private fun makeToast(context: Context, messageId: Int, onLinkDeviceFailure: () -> Unit) {
  Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
  onLinkDeviceFailure()
}

@DayNightPreviews
@Composable
private fun LinkDeviceQrScanScreenPreview() {
  Previews.Preview {
    LinkDeviceQrScanScreen(
      hasPermission = true,
      onRequestPermissions = {},
      cameraState = CameraScreenState(),
      cameraEmitter = {},
      qrCodeState = LinkDeviceSettingsState.QrCodeState.NONE,
      onQrCodeAccepted = {},
      onQrCodeDismissed = {},
      linkDeviceResult = LinkDeviceResult.None,
      onLinkDeviceSuccess = {},
      onLinkDeviceFailure = {},
      navController = null
    )
  }
}

@DayNightPreviews
@Composable
private fun LinkDeviceQrScanScreenNoPermissionPreview() {
  Previews.Preview {
    LinkDeviceQrScanScreen(
      hasPermission = false,
      onRequestPermissions = {},
      cameraState = CameraScreenState(),
      cameraEmitter = {},
      qrCodeState = LinkDeviceSettingsState.QrCodeState.NONE,
      onQrCodeAccepted = {},
      onQrCodeDismissed = {},
      linkDeviceResult = LinkDeviceResult.None,
      onLinkDeviceSuccess = {},
      onLinkDeviceFailure = {},
      navController = null
    )
  }
}

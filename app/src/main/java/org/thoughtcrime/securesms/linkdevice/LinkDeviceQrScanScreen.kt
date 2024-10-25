package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.Dialogs
import org.signal.qr.QrScannerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist
import org.thoughtcrime.securesms.qr.QrScanScreens
import java.util.concurrent.TimeUnit

/**
 * A screen that allows you to scan a QR code to link a device
 */
@Composable
fun LinkDeviceQrScanScreen(
  hasPermission: Boolean,
  onRequestPermissions: () -> Unit,
  showFrontCamera: Boolean?,
  qrCodeState: LinkDeviceSettingsState.QrCodeState,
  onQrCodeScanned: (String) -> Unit,
  onQrCodeAccepted: () -> Unit,
  onQrCodeDismissed: () -> Unit,
  onQrCodeRetry: () -> Unit,
  linkDeviceResult: LinkDeviceResult,
  onLinkDeviceSuccess: () -> Unit,
  onLinkDeviceFailure: () -> Unit,
  modifier: Modifier = Modifier
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current

  when (qrCodeState) {
    LinkDeviceSettingsState.QrCodeState.NONE -> {
      Unit
    }
    LinkDeviceSettingsState.QrCodeState.VALID -> {
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
        onConfirm = onQrCodeRetry,
        dismiss = stringResource(id = android.R.string.cancel),
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
    ) {
      QrScanScreens.QrScanScreen(
        factory = { factoryContext ->
          val view = QrScannerView(factoryContext)
          view.qrData
            .throttleFirst(3000, TimeUnit.MILLISECONDS)
            .subscribe { data ->
              onQrCodeScanned(data)
            }
          view
        },
        update = { view: QrScannerView ->
          view.start(lifecycleOwner = lifecycleOwner, forceLegacy = CameraXModelBlocklist.isBlocklisted())
          if (showFrontCamera != null) {
            view.toggleCamera()
          }
        },
        hasPermission = hasPermission,
        onRequestPermissions = onRequestPermissions,
        qrHeaderLabelString = stringResource(R.string.AddLinkDeviceFragment__scan_the_qr_code)
      )
    }
  }
}

private fun makeToast(context: Context, messageId: Int, onLinkDeviceFailure: () -> Unit) {
  Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
  onLinkDeviceFailure()
}

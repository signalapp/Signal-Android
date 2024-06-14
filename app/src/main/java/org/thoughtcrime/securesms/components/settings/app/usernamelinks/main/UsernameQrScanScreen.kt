package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.ui.Dialogs
import org.signal.core.ui.theme.SignalTheme
import org.signal.qr.QrScannerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist
import org.thoughtcrime.securesms.qr.QrScanScreens
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.concurrent.TimeUnit

/**
 * A screen that allows you to scan a QR code to start a chat.
 */
@Composable
fun UsernameQrScanScreen(
  lifecycleOwner: LifecycleOwner,
  disposables: CompositeDisposable,
  qrScanResult: QrScanResult?,
  onQrCodeScanned: (String) -> Unit,
  onQrResultHandled: () -> Unit,
  onOpenCameraClicked: () -> Unit,
  onOpenGalleryClicked: () -> Unit,
  onRecipientFound: (Recipient) -> Unit,
  hasCameraPermission: Boolean,
  modifier: Modifier = Modifier
) {
  when (qrScanResult) {
    QrScanResult.InvalidData -> {
      QrScanResultDialog(message = stringResource(R.string.UsernameLinkSettings_qr_result_invalid), onDismiss = onQrResultHandled)
    }

    QrScanResult.NetworkError -> {
      QrScanResultDialog(message = stringResource(R.string.UsernameLinkSettings_qr_result_network_error), onDismiss = onQrResultHandled)
    }

    QrScanResult.QrNotFound -> {
      QrScanResultDialog(
        title = stringResource(R.string.UsernameLinkSettings_qr_code_not_found),
        message = stringResource(R.string.UsernameLinkSettings_try_scanning_another_image_containing_a_signal_qr_code),
        onDismiss = onQrResultHandled
      )
    }

    is QrScanResult.NotFound -> {
      if (qrScanResult.username != null) {
        QrScanResultDialog(message = stringResource(R.string.UsernameLinkSettings_qr_result_not_found, qrScanResult.username), onDismiss = onQrResultHandled)
      } else {
        QrScanResultDialog(message = stringResource(R.string.UsernameLinkSettings_qr_result_not_found_no_username), onDismiss = onQrResultHandled)
      }
    }

    is QrScanResult.Success -> {
      onRecipientFound(qrScanResult.recipient)
    }

    null -> {}
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
        factory = { context ->
          val view = QrScannerView(context)
          disposables += view.qrData.throttleFirst(3000, TimeUnit.MILLISECONDS).subscribe { data ->
            onQrCodeScanned(data)
          }
          view
        },
        update = { view ->
          view.start(lifecycleOwner = lifecycleOwner, forceLegacy = CameraXModelBlocklist.isBlocklisted())
        },
        hasPermission = hasCameraPermission,
        onRequestPermissions = onOpenCameraClicked,
        qrHeaderLabelString = ""
      )
      FloatingActionButton(
        shape = CircleShape,
        containerColor = SignalTheme.colors.colorSurface1,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        onClick = onOpenGalleryClicked
      ) {
        Image(
          painter = painterResource(id = R.drawable.symbol_album_24),
          contentDescription = null,
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Text(
        text = stringResource(R.string.UsernameLinkSettings_qr_scan_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun QrScanResultDialog(title: String? = null, message: String, onDismiss: () -> Unit) {
  Dialogs.SimpleMessageDialog(
    title = title,
    message = message,
    dismiss = stringResource(id = android.R.string.ok),
    onDismiss = onDismiss
  )
}

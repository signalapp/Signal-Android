package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.ui.Dialogs
import org.signal.qr.QrScannerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist
import org.thoughtcrime.securesms.util.CommunicationActions
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
  modifier: Modifier = Modifier
) {
  when (qrScanResult) {
    QrScanResult.InvalidData -> {
      QrScanResultDialog(stringResource(R.string.UsernameLinkSettings_qr_result_invalid), onDismiss = onQrResultHandled)
    }
    QrScanResult.NetworkError -> {
      QrScanResultDialog(stringResource(R.string.UsernameLinkSettings_qr_result_network_error), onDismiss = onQrResultHandled)
    }
    is QrScanResult.NotFound -> {
      if (qrScanResult.username != null) {
        QrScanResultDialog(stringResource(R.string.UsernameLinkSettings_qr_result_not_found, qrScanResult.username), onDismiss = onQrResultHandled)
      } else {
        QrScanResultDialog(stringResource(R.string.UsernameLinkSettings_qr_result_not_found_no_username), onDismiss = onQrResultHandled)
      }
    }
    is QrScanResult.Success -> {
      CommunicationActions.startConversation(LocalContext.current, qrScanResult.recipient, null)
      onQrResultHandled()
    }
    null -> {}
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight()
  ) {
    AndroidView(
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
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f, true)
        .drawWithContent {
          drawContent()
          drawQrCrosshair()
        }
    )

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
private fun QrScanResultDialog(message: String, onDismiss: () -> Unit) {
  Dialogs.SimpleMessageDialog(
    message = message,
    dismiss = stringResource(id = android.R.string.ok),
    onDismiss = onDismiss
  )
}

private fun DrawScope.drawQrCrosshair() {
  val crosshairWidth: Float = size.minDimension * 0.6f
  val clearWidth: Float = crosshairWidth * 0.75f

  // Draw a full white rounded rect...
  drawRoundRect(
    color = Color.White,
    topLeft = center - Offset(crosshairWidth / 2, crosshairWidth / 2),
    style = Stroke(width = 3.dp.toPx()),
    size = Size(crosshairWidth, crosshairWidth),
    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
  )

  // ...then cut out the middle parts with BlendMode.Clear to leave us with just the corners
  drawRect(
    color = Color.White,
    topLeft = Offset(center.x - clearWidth / 2, 0f),
    style = Fill,
    size = Size(clearWidth, size.height),
    blendMode = BlendMode.Clear
  )

  drawRect(
    color = Color.White,
    topLeft = Offset(0f, center.y - clearWidth / 2),
    style = Fill,
    size = Size(size.width, clearWidth),
    blendMode = BlendMode.Clear
  )
}

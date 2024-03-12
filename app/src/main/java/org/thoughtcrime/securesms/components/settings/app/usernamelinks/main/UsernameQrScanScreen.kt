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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.ui.Dialogs
import org.signal.core.ui.theme.SignalTheme
import org.signal.qr.QrScannerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist
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
  onOpenGalleryClicked: () -> Unit,
  onRecipientFound: (Recipient) -> Unit,
  modifier: Modifier = Modifier
) {
  val path = remember { Path() }

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
          .fillMaxHeight()
          .drawWithContent {
            drawContent()
            drawQrCrosshair(path)
          }
      )

      FloatingActionButton(
        shape = CircleShape,
        containerColor = SignalTheme.colors.colorSurface1,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 24.dp),
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

private fun DrawScope.drawQrCrosshair(path: Path) {
  val crosshairWidth: Float = size.minDimension * 0.6f
  val crosshairLineLength = crosshairWidth * 0.125f

  val topLeft = center - Offset(crosshairWidth / 2, crosshairWidth / 2)
  val topRight = center + Offset(crosshairWidth / 2, -crosshairWidth / 2)
  val bottomRight = center + Offset(crosshairWidth / 2, crosshairWidth / 2)
  val bottomLeft = center + Offset(-crosshairWidth / 2, crosshairWidth / 2)

  path.reset()

  drawPath(
    path = path.apply {
      moveTo(topLeft.x, topLeft.y + crosshairLineLength)
      lineTo(topLeft.x, topLeft.y)
      lineTo(topLeft.x + crosshairLineLength, topLeft.y)

      moveTo(topRight.x - crosshairLineLength, topRight.y)
      lineTo(topRight.x, topRight.y)
      lineTo(topRight.x, topRight.y + crosshairLineLength)

      moveTo(bottomRight.x, bottomRight.y - crosshairLineLength)
      lineTo(bottomRight.x, bottomRight.y)
      lineTo(bottomRight.x - crosshairLineLength, bottomRight.y)

      moveTo(bottomLeft.x + crosshairLineLength, bottomLeft.y)
      lineTo(bottomLeft.x, bottomLeft.y)
      lineTo(bottomLeft.x, bottomLeft.y - crosshairLineLength)
    },
    color = Color.White,
    style = Stroke(
      width = 3.dp.toPx(),
      pathEffect = PathEffect.cornerPathEffect(10.dp.toPx())
    )
  )
}

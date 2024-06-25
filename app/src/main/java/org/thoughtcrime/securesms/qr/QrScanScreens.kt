package org.thoughtcrime.securesms.qr

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate
import org.signal.core.ui.Buttons
import org.signal.qr.QrScannerView
import org.thoughtcrime.securesms.R

object QrScanScreens {
  /**
   * Full-screen qr scanning screen with permission-asking UI
   */
  @Composable
  fun QrScanScreen(
    factory: (Context) -> QrScannerView,
    update: (QrScannerView) -> Unit = NoOpUpdate,
    hasPermission: Boolean,
    onRequestPermissions: () -> Unit = {},
    qrHeaderLabelString: String
  ) {
    val path = remember { Path() }

    Column(
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, true)
      ) {
        AndroidView(
          factory = factory,
          update = update,
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .drawWithContent {
              drawContent()
              if (hasPermission) {
                drawQrCrosshair(path)
              }
            }
        )
        if (!hasPermission) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).padding(48.dp)
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
        } else {
          Text(
            text = qrHeaderLabelString,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth()
          )
        }
      }
    }
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
}

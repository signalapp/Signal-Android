package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.R

/**
 * Shows a QRCode that represents the provided data. Includes a Signal logo in the middle.
 */
@Composable
fun QrCode(
  data: QrCodeData,
  modifier: Modifier = Modifier,
  foregroundColor: Color = Color.Black,
  backgroundColor: Color = Color.White,
  deadzonePercent: Float = 0.4f
) {
  val logo = ImageBitmap.imageResource(R.drawable.qrcode_logo)

  Column(
    modifier = modifier
      .drawBehind {
        drawQr(
          data = data,
          foregroundColor = foregroundColor,
          backgroundColor = backgroundColor,
          deadzonePercent = deadzonePercent,
          logo = logo
        )
      }
  ) {
  }
}

private fun DrawScope.drawQr(
  data: QrCodeData,
  foregroundColor: Color,
  backgroundColor: Color,
  deadzonePercent: Float,
  logo: ImageBitmap
) {
  // We want an even number of dots on either side of the deadzone
  val candidateDeadzoneWidth: Int = (data.width * deadzonePercent).toInt()
  val deadzoneWidth: Int = if ((data.width - candidateDeadzoneWidth) % 2 == 0) {
    candidateDeadzoneWidth
  } else {
    candidateDeadzoneWidth + 1
  }

  val candidateDeadzoneHeight: Int = (data.height * deadzonePercent).toInt()
  val deadzoneHeight: Int = if ((data.height - candidateDeadzoneHeight) % 2 == 0) {
    candidateDeadzoneHeight
  } else {
    candidateDeadzoneHeight + 1
  }

  val deadzoneStartX: Int = (data.width - deadzoneWidth) / 2
  val deadzoneEndX: Int = deadzoneStartX + deadzoneWidth
  val deadzoneStartY: Int = (data.height - deadzoneHeight) / 2
  val deadzoneEndY: Int = deadzoneStartY + deadzoneHeight

  val cellWidthPx: Float = size.width / data.width
  val cellRadiusPx = cellWidthPx / 2

  for (x in 0 until data.width) {
    for (y in 0 until data.height) {
      if (x < deadzoneStartX || x >= deadzoneEndX || y < deadzoneStartY || y >= deadzoneEndY) {
        drawCircle(
          color = if (data.get(x, y)) foregroundColor else backgroundColor,
          radius = cellRadiusPx,
          center = Offset(x * cellWidthPx + cellRadiusPx, y * cellWidthPx + cellRadiusPx)
        )
      }
    }
  }

  // Logo border
  val deadzonePaddingPercent = 0.02f
  val logoBorderRadiusPx = ((deadzonePercent - deadzonePaddingPercent) * size.width) / 2
  drawCircle(
    color = foregroundColor,
    radius = logoBorderRadiusPx,
    style = Stroke(width = cellWidthPx * 0.7f),
    center = this.center
  )

  // Logo
  val logoWidthPx = ((deadzonePercent / 2) * size.width).toInt()
  val logoOffsetPx = ((size.width - logoWidthPx) / 2).toInt()
  drawImage(
    image = logo,
    dstOffset = IntOffset(logoOffsetPx, logoOffsetPx),
    dstSize = IntSize(logoWidthPx, logoWidthPx),
    colorFilter = ColorFilter.tint(foregroundColor)
  )

  for (eye in data.eyes()) {
    val strokeWidth = cellWidthPx

    // Clear the already-drawn dots
    drawRect(
      color = backgroundColor,
      topLeft = Offset(
        x = eye.position.first * cellWidthPx,
        y = eye.position.second * cellWidthPx
      ),
      size = Size(eye.size * cellWidthPx + cellRadiusPx, eye.size * cellWidthPx)
    )

    // Outer square
    drawRoundRect(
      color = foregroundColor,
      topLeft = Offset(
        x = eye.position.first * cellWidthPx + strokeWidth / 2,
        y = eye.position.second * cellWidthPx + strokeWidth / 2
      ),
      size = Size((eye.size - 1) * cellWidthPx, (eye.size - 1) * cellWidthPx),
      cornerRadius = CornerRadius(cellRadiusPx * 2, cellRadiusPx * 2),
      style = Stroke(width = strokeWidth)
    )

    // Inner square
    drawRoundRect(
      color = foregroundColor,
      topLeft = Offset(
        x = (eye.position.first + 2) * cellWidthPx,
        y = (eye.position.second + 2) * cellWidthPx
      ),
      size = Size((eye.size - 4) * cellWidthPx, (eye.size - 4) * cellWidthPx),
      cornerRadius = CornerRadius(cellRadiusPx, cellRadiusPx)
    )
  }
}

@Preview
@Composable
private fun Preview() {
  Surface {
    QrCode(
      data = QrCodeData.forData("https://signal.org", 64),
      modifier = Modifier
        .width(100.dp)
        .height(100.dp),
      deadzonePercent = 0.3f
    )
  }
}

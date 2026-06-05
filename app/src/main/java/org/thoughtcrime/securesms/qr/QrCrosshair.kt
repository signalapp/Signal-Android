package org.thoughtcrime.securesms.qr

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Four-corner crosshair overlay used to frame the QR scan target in camera viewfinders.
 * The crosshair is sized to 60% of the smaller dimension of its layout.
 */
@Composable
fun QrCrosshair(modifier: Modifier = Modifier) {
  val path = remember { Path() }

  Canvas(modifier = modifier) {
    val crosshairWidth = size.minDimension * 0.6f
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

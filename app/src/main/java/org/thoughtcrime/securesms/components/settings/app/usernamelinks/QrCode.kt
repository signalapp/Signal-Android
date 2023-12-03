package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.R
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Shows a QRCode that represents the provided data. Includes a Signal logo in the middle.
 */
@Composable
fun QrCode(
  data: QrCodeData,
  modifier: Modifier = Modifier,
  foregroundColor: Color = Color.Black,
  backgroundColor: Color = Color.White,
  deadzonePercent: Float = 0.35f
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

fun DrawScope.drawQr(
  data: QrCodeData,
  foregroundColor: Color,
  backgroundColor: Color,
  deadzonePercent: Float,
  logo: ImageBitmap?
) {
  val deadzonePaddingPercent = 0.045f

  // We want an even number of dots on either side of the deadzone
  val deadzoneRadius: Int = (data.height * (deadzonePercent + deadzonePaddingPercent)).toInt().let { candidateDeadzoneHeight ->
    if ((data.height - candidateDeadzoneHeight) % 2 == 0) {
      candidateDeadzoneHeight
    } else {
      candidateDeadzoneHeight + 1
    }
  } / 2

  val cellWidthPx: Float = size.width / data.width
  val cornerRadius = CornerRadius(7f, 7f)
  val deadzone = Circle(center = IntOffset(data.width / 2, data.height / 2), radius = deadzoneRadius)

  for (x in 0 until data.width) {
    for (y in 0 until data.height) {
      val position = IntOffset(x, y)

      if (data.get(position) && !deadzone.contains(position)) {
        val filledAbove = IntOffset(x, y - 1).let { data.get(it) && !deadzone.contains(it) }
        val filledBelow = IntOffset(x, y + 1).let { data.get(it) && !deadzone.contains(it) }
        val filledLeft = IntOffset(x - 1, y).let { data.get(it) && !deadzone.contains(it) }
        val filledRight = IntOffset(x + 1, y).let { data.get(it) && !deadzone.contains(it) }

        val path = Path().apply {
          addRoundRect(
            RoundRect(
              rect = Rect(
                topLeft = Offset(floor(x * cellWidthPx), floor(y * cellWidthPx - 1)),
                bottomRight = Offset(ceil((x + 1) * cellWidthPx), ceil((y + 1) * cellWidthPx + 1))
              ),
              topLeft = if (filledAbove || filledLeft) CornerRadius.Zero else cornerRadius,
              topRight = if (filledAbove || filledRight) CornerRadius.Zero else cornerRadius,
              bottomLeft = if (filledBelow || filledLeft) CornerRadius.Zero else cornerRadius,
              bottomRight = if (filledBelow || filledRight) CornerRadius.Zero else cornerRadius
            )
          )
        }

        drawPath(
          path = path,
          color = if (data.get(position)) foregroundColor else backgroundColor
        )
      }
    }
  }

  // Logo border
  val logoBorderRadiusPx = ((deadzonePercent - deadzonePaddingPercent) * size.width) / 2
  drawCircle(
    color = foregroundColor,
    radius = logoBorderRadiusPx,
    style = Stroke(width = cellWidthPx * 0.75f),
    center = this.center
  )

  // Logo
  val logoWidthPx = (((deadzonePercent - deadzonePaddingPercent) * 0.6f) * size.width).toInt()
  val logoOffsetPx = ((size.width - logoWidthPx) / 2).toInt()
  if (logo != null) {
    drawImage(
      image = logo,
      dstOffset = IntOffset(logoOffsetPx, logoOffsetPx),
      dstSize = IntSize(logoWidthPx, logoWidthPx),
      colorFilter = ColorFilter.tint(foregroundColor)
    )
  }
}

@Preview
@Composable
private fun Preview() {
  Surface {
    QrCode(
      data = QrCodeData.forData("https://signal.org", 64),
      modifier = Modifier.size(350.dp)
    )
  }
}

private data class Circle(
  val center: IntOffset,
  val radius: Int
) {
  fun contains(position: IntOffset): Boolean {
    val diff = center - position
    return diff.x * diff.x + diff.y * diff.y < radius * radius
  }
}

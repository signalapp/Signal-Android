package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.theme.SignalTheme

/**
 * Renders a QR code and username as a badge.
 */
@Composable
fun QrCodeBadge(data: QrCodeData?, colorScheme: UsernameQrCodeColorScheme, username: String, modifier: Modifier = Modifier) {
  val borderColor by animateColorAsState(targetValue = colorScheme.borderColor)
  val foregroundColor by animateColorAsState(targetValue = colorScheme.foregroundColor)
  val elevation by animateFloatAsState(targetValue = if (colorScheme == UsernameQrCodeColorScheme.White) 10f else 0f)
  val textColor by animateColorAsState(targetValue = if (colorScheme == UsernameQrCodeColorScheme.White) Color.Black else Color.White)

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 59.dp, vertical = 24.dp),
    color = borderColor,
    shape = RoundedCornerShape(24.dp),
    shadowElevation = elevation.dp
  ) {
    Column {
      Surface(
        modifier = Modifier
          .padding(
            top = 32.dp,
            start = 40.dp,
            end = 40.dp,
            bottom = 16.dp
          )
          .aspectRatio(1f)
          .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
      ) {
        if (data != null) {
          QrCode(
            data = data,
            modifier = Modifier.padding(20.dp),
            foregroundColor = foregroundColor,
            backgroundColor = Color.White
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .fillMaxHeight(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              color = colorScheme.borderColor,
              modifier = Modifier.size(56.dp)
            )
          }
        }
      }

      Text(
        text = username,
        color = textColor,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.W600,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = 40.dp,
            end = 40.dp,
            bottom = 32.dp
          )
      )
    }
  }
}

@Preview
@Composable
private fun PreviewWithCode() {
  SignalTheme(isDarkMode = false) {
    Surface {
      QrCodeBadge(
        data = QrCodeData.forData("https://signal.org", 64),
        colorScheme = UsernameQrCodeColorScheme.Blue,
        username = "parker.42"
      )
    }
  }
}

@Preview
@Composable
private fun PreviewWithoutCode() {
  SignalTheme(isDarkMode = false) {
    Surface {
      QrCodeBadge(
        data = null,
        colorScheme = UsernameQrCodeColorScheme.Blue,
        username = "parker.42"
      )
    }
  }
}

package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R

/**
 * Renders a QR code and username as a badge.
 */
@Composable
fun QrCodeBadge(
  data: QrCodeState,
  colorScheme: UsernameQrCodeColorScheme,
  username: String,
  modifier: Modifier = Modifier,
  usernameCopyable: Boolean = false,
  onClick: ((String) -> Unit) = {}
) {
  val borderColor by animateColorAsState(targetValue = colorScheme.borderColor, label = "border")
  val foregroundColor by animateColorAsState(targetValue = colorScheme.foregroundColor, label = "foreground")
  val elevation by animateFloatAsState(targetValue = if (colorScheme == UsernameQrCodeColorScheme.White) 10f else 0f, label = "elevation")
  val textColor by animateColorAsState(targetValue = colorScheme.textColor, label = "textColor")

  Surface(
    modifier = modifier,
    color = borderColor,
    shape = RoundedCornerShape(24.dp),
    shadowElevation = elevation.dp
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.width(296.dp)
    ) {
      Surface(
        modifier = Modifier
          .padding(
            top = 32.dp,
            start = 40.dp,
            end = 40.dp
          )
          .aspectRatio(1f)
          .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
      ) {
        if (data is QrCodeState.Present) {
          QrCode(
            data = data.data,
            modifier = Modifier
              .border(
                width = 2.dp,
                color = colorScheme.outlineColor,
                shape = RoundedCornerShape(size = 12.dp)
              )
              .padding(16.dp),
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
            if (data is QrCodeState.Loading) {
              CircularProgressIndicator(
                color = colorScheme.borderColor,
                modifier = Modifier.size(56.dp)
              )
            } else if (data is QrCodeState.NotSet) {
              Image(
                painter = painterResource(id = R.drawable.symbol_error_circle_24),
                contentDescription = stringResource(id = R.string.UsernameLinkSettings_link_not_set_label),
                colorFilter = ColorFilter.tint(colorResource(R.color.core_grey_25)),
                modifier = Modifier
                  .width(28.dp)
                  .height(28.dp)
              )
            }
          }
        }
      }

      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .padding(
            start = 32.dp,
            end = 32.dp,
            top = 8.dp,
            bottom = 28.dp
          )
          .clip(RoundedCornerShape(8.dp))
          .clickable(
            enabled = usernameCopyable,
            onClick = { onClick(username) }
          )
          .padding(8.dp)
      ) {
        if (usernameCopyable) {
          Image(
            painter = painterResource(id = R.drawable.symbol_copy_android_24),
            contentDescription = null,
            colorFilter = if (colorScheme == UsernameQrCodeColorScheme.White) {
              ColorFilter.tint(Color.Black)
            } else {
              ColorFilter.tint(Color.White)
            }
          )
        }

        Text(
          text = username,
          color = textColor,
          fontSize = 20.sp,
          lineHeight = 26.sp,
          fontWeight = FontWeight.W600,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(start = 6.dp)
        )
      }
    }
  }
}

@Preview(name = "Light Theme", group = "ShortName", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "ShortName", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewWithCodeShort() {
  SignalTheme {
    Surface {
      Column {
        QrCodeBadge(
          data = QrCodeState.Present(QrCodeData.forData("https://signal.org")),
          colorScheme = UsernameQrCodeColorScheme.Blue,
          username = "parker.42",
          usernameCopyable = false
        )
        QrCodeBadge(
          data = QrCodeState.Present(QrCodeData.forData("https://signal.org")),
          colorScheme = UsernameQrCodeColorScheme.Blue,
          username = "parker.42",
          usernameCopyable = true
        )
      }
    }
  }
}

@Preview(group = "LongName")
@Composable
private fun PreviewWithCodeLong() {
  SignalTheme {
    Surface {
      Column {
        QrCodeBadge(
          data = QrCodeState.Present(QrCodeData.forData("https://signal.org")),
          colorScheme = UsernameQrCodeColorScheme.Blue,
          username = "TheAmazingSpiderMan.42",
          usernameCopyable = false
        )
        Spacer(modifier = Modifier.height(8.dp))
        QrCodeBadge(
          data = QrCodeState.Present(QrCodeData.forData("https://signal.org")),
          colorScheme = UsernameQrCodeColorScheme.Blue,
          username = "TheAmazingSpiderMan.42",
          usernameCopyable = true
        )
      }
    }
  }
}

@Preview(group = "Colors", heightDp = 1500)
@Composable
private fun PreviewAllColorsP1() {
  SignalTheme(isDarkMode = false) {
    Surface {
      Column {
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Blue)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.White)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Green)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Grey)
      }
    }
  }
}

@Preview(group = "Colors", heightDp = 1500)
@Composable
private fun PreviewAllColorsP2() {
  SignalTheme(isDarkMode = false) {
    Surface {
      Column {
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Pink)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Orange)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Purple)
        Spacer(modifier = Modifier.height(8.dp))
        SampleCode(colorScheme = UsernameQrCodeColorScheme.Tan)
      }
    }
  }
}

@Composable
private fun SampleCode(colorScheme: UsernameQrCodeColorScheme) {
  QrCodeBadge(
    data = QrCodeState.Present(QrCodeData.forData("https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdf")),
    colorScheme = colorScheme,
    username = "parker.42"
  )
}

@Preview(name = "Light Theme", group = "Loading", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Loading", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewLoading() {
  SignalTheme {
    Surface {
      QrCodeBadge(
        data = QrCodeState.Loading,
        colorScheme = UsernameQrCodeColorScheme.Blue,
        username = "parker.42"
      )
    }
  }
}

@Preview(name = "Light Theme", group = "NotSet", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "NotSet", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNotSet() {
  SignalTheme {
    Surface {
      QrCodeBadge(
        data = QrCodeState.NotSet,
        colorScheme = UsernameQrCodeColorScheme.Blue,
        username = "parker.42"
      )
    }
  }
}

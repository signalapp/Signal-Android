package org.thoughtcrime.securesms.components.settings.app.subscription.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

@Composable
fun PayPalButton(
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val overlayColor = colorResource(org.signal.core.ui.R.color.signal_light_colorTransparent3)
  Buttons.LargeTonal(
    onClick = onClick,
    enabled = enabled,
    contentPadding = PaddingValues.Zero,
    modifier = modifier.drawWithContent {
      drawContent()

      if (!enabled) {
        drawRoundRect(
          color = overlayColor,
          cornerRadius = CornerRadius(500f, 500f)
        )
      }
    },
    colors = ButtonDefaults.buttonColors(
      containerColor = Color(0xFFF6C757),
      disabledContainerColor = Color(0xFFF6C757)
    )
  ) {
    Image(
      imageVector = ImageVector.vectorResource(R.drawable.paypal),
      contentDescription = stringResource(R.string.BackupsTypeSettingsFragment__paypal)
    )
  }
}

@DayNightPreviews
@Composable
fun PayPalButtonPreview() {
  Previews.Preview {
    PayPalButton(
      enabled = true,
      onClick = {},
      modifier = Modifier
        .horizontalGutters()
        .fillMaxWidth()
        .height(44.dp)
    )
  }
}

@DayNightPreviews
@Composable
fun PayPalButtonDisabledPreview() {
  Previews.Preview {
    PayPalButton(
      enabled = false,
      onClick = {},
      modifier = Modifier
        .horizontalGutters()
        .fillMaxWidth()
        .height(44.dp)
    )
  }
}

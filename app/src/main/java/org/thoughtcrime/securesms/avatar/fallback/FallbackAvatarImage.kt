/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.fallback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.avatar.AvatarRenderer
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.AvatarColorPair

@Composable
fun FallbackAvatarImage(
  fallbackAvatar: FallbackAvatar,
  modifier: Modifier = Modifier,
  shape: Shape = CircleShape
) {
  if (fallbackAvatar is FallbackAvatar.Transparent) {
    Box(modifier = modifier)
    return
  }

  val context = LocalContext.current
  val colorPair = remember(fallbackAvatar) {
    AvatarColorPair.create(context, fallbackAvatar.color)
  }

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .background(Color(colorPair.backgroundColor), shape)
  ) {
    when (fallbackAvatar) {
      is FallbackAvatar.Resource -> {
        val size = remember(maxWidth) {
          FallbackAvatar.getSizeByDp(maxWidth)
        }

        val padding = remember(maxWidth) {
          ((maxWidth.value - (maxWidth.value * FallbackAvatar.ICON_TO_BACKGROUND_SCALE)) / 2).dp
        }

        Icon(
          painter = painterResource(fallbackAvatar.getIconBySize(size)),
          contentDescription = null,
          tint = Color(colorPair.foregroundColor),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        )
      }

      is FallbackAvatar.Text -> {
        val size = DimensionUnit.DP.toPixels(maxWidth.value) * 0.8f
        val textSize = DimensionUnit.PIXELS.toDp(Avatars.getTextSizeForLength(context, fallbackAvatar.content, size, size))

        // TODO [alex] -- Handle emoji

        Text(
          text = fallbackAvatar.content,
          color = Color(colorPair.foregroundColor),
          fontSize = TextUnit(textSize, TextUnitType.Sp),
          fontFamily = FontFamily(AvatarRenderer.getTypeface(context))
        )
      }
      FallbackAvatar.Transparent -> {}
    }
  }
}

@SignalPreview
@Composable
fun FallbackAvatarImagePreview() {
  Previews.Preview {
    Column {
      Text(text = "Compose - Large")
      FallbackAvatarImage(
        fallbackAvatar = FallbackAvatar.Text("AE", AvatarColor.A100),
        modifier = Modifier.size(160.dp)
      )
      Text(text = "Compose - Medium")
      FallbackAvatarImage(
        fallbackAvatar = FallbackAvatar.Text("AE", AvatarColor.A100),
        modifier = Modifier.size(64.dp)
      )
      Text(text = "Compose - Small")
      FallbackAvatarImage(
        fallbackAvatar = FallbackAvatar.Text("AE", AvatarColor.A100),
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

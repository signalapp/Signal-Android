/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.R

/**
 * Signal icon library with all available icons.
 */
enum class SignalIcons(private val icon: SignalIcon) : SignalIcon by icon {
  Keyboard(icon(R.drawable.ic_keyboard_24)),
  Camera(icon(R.drawable.symbol_camera_24)),
  Phone(icon(R.drawable.symbol_phone_24)),
  QrCode(icon(R.drawable.symbol_qrcode_24))
}

private fun icon(@DrawableRes id: Int) = SignalIcon.DrawableIcon(id)
private fun icon(image: ImageVector) = SignalIcon.ImageVectorIcon(image)

sealed interface SignalIcon {
  @get:Composable
  val painter: Painter

  /**
   * Icon backed by an XML drawable resource.
   */
  @JvmInline
  value class DrawableIcon(@get:DrawableRes private val drawableId: Int) : SignalIcon {
    @get:Composable
    override val painter: Painter
      get() = painterResource(drawableId)
  }

  /**
   * Icon backed by an [ImageVector].
   */
  @JvmInline
  value class ImageVectorIcon(val image: ImageVector) : SignalIcon {
    @get:Composable
    override val painter: Painter
      get() = rememberVectorPainter(image)
  }
}

@DayNightPreviews
@Composable
private fun SignalIconsPreview() {
  Previews.Preview {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 80.dp),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      items(SignalIcons.entries.sortedBy { it.name }) { icon ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            painter = icon.painter,
            contentDescription = icon.name,
            modifier = Modifier.size(24.dp)
          )
          Text(
            text = icon.name,
            style = MaterialTheme.typography.labelSmall
          )
        }
      }
    }
  }
}

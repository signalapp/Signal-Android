/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

sealed interface BackupsIconColors {
  @get:Composable
  val foreground: Brush

  @get:Composable
  val background: Color

  data object Normal : BackupsIconColors {
    override val foreground: Brush
      @Composable get() = remember {
        Brush.linearGradient(
          colors = listOf(Color(0xFF316ED0), Color(0xFF558BE2)),
          start = Offset(x = 0f, y = Float.POSITIVE_INFINITY),
          end = Offset(x = Float.POSITIVE_INFINITY, y = 0f)
        )
      }

    override val background: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
  }

  object Warning : BackupsIconColors {
    override val foreground: Brush @Composable get() = SolidColor(Color(0xFFC86600))
    override val background: Color @Composable get() = Color(0xFFF9E4B6)
  }

  object Error : BackupsIconColors {
    override val foreground: Brush @Composable get() = SolidColor(MaterialTheme.colorScheme.error)
    override val background: Color @Composable get() = Color(0xFFFFD9D9)
  }
}

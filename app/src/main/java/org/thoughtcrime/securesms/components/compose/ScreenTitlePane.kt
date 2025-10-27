/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.window.WindowSizeClass

/**
 * Displays the screen title for split-pane UIs on tablets and foldable devices.
 */
@Composable
fun ScreenTitlePane(
  title: String,
  modifier: Modifier = Modifier
) {
  val windowSizeClass = WindowSizeClass.rememberWindowSizeClass()

  Text(
    text = title,
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
      .padding(
        start = if (windowSizeClass.isExtended()) 80.dp else 20.dp,
        end = 20.dp,
        bottom = 12.dp
      )
  )
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme

object BottomSheets {
  /**
   * Handle for bottom sheets
   */
  @Composable
  fun Handle(modifier: Modifier = Modifier) {
    Box(
      modifier = modifier
        .size(width = 48.dp, height = 22.dp)
        .padding(vertical = 10.dp)
        .clip(RoundedCornerShape(1000.dp))
        .background(MaterialTheme.colorScheme.outline)
    )
  }
}

@Preview
@Composable
private fun HandlePreview() {
  SignalTheme(isDarkMode = false) {
    BottomSheets.Handle()
  }
}

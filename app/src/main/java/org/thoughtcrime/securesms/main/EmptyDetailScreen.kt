/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.R

/**
 * Displayed when the user has not selected content for a given tab.
 */
@Composable
fun EmptyDetailScreen() {
  Box(
    modifier = Modifier
      .background(color = MaterialTheme.colorScheme.surface)
      .fillMaxSize()
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(id = R.drawable.ic_signal_logo_large),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
      modifier = Modifier
        .size(80.dp)
        .align(Alignment.Center)
    )
  }
}

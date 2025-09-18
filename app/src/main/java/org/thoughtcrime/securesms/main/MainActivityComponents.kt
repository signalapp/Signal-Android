/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.thoughtcrime.securesms.R

@Composable
fun EmptyDetailScreen(
  contentLayoutData: MainContentLayoutData
) {
  Box(
    modifier = Modifier
      .padding(end = contentLayoutData.detailPaddingEnd)
      .clip(contentLayoutData.shape)
      .background(color = MaterialTheme.colorScheme.surface)
      .fillMaxSize()
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_signal_logo_large),
      contentDescription = null,
      tint = Color(0x58607152),
      modifier = Modifier.align(Alignment.Center)
    )
  }
}

@Composable
fun MainActivityDetailContainer(
  contentLayoutData: MainContentLayoutData,
  content: @Composable () -> Unit
) {
  Box(
    modifier = Modifier
      .padding(end = contentLayoutData.detailPaddingEnd)
      .clip(contentLayoutData.shape)
      .background(color = MaterialTheme.colorScheme.surface)
      .fillMaxSize()
  ) {
    content()
  }
}

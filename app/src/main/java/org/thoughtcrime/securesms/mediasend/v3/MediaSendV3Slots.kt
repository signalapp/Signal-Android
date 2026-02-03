/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun MediaSendV3PlaceholderScreen(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = text)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * A round button for choosing the emoji beside a piece of text. Shows [emoji], or an add-emoji icon while it's empty.
 */
@Composable
fun EmojiButton(
  emoji: String,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(CircleShape)
      .clickable(onClick = onClick, role = Role.Button)
      .semantics { this.contentDescription = contentDescription }
      .padding(8.dp)
  ) {
    if (emoji.isNotEmpty()) {
      EmojiText(text = emoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
    } else {
      Icon(
        imageVector = SignalIcons.EmojiPlus.imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * The bottom sheet an emoji picker is shown in, such as the one an [EmojiButton] opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
  onDismissRequest: () -> Unit,
  content: @Composable () -> Unit
) {
  BottomSheets.BottomSheet(
    onDismissRequest = onDismissRequest,
    dragHandle = null,
    containerColor = SignalTheme.colors.colorSurface1,
    // The picker keeps itself clear of the navigation bar and keyboard at the bottom.
    contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) }
  ) {
    content()
  }
}

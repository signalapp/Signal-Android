/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * A consistent ActionMode top-bar for dealing with multiselect scenarios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionModeTopBar(
  title: String,
  onCloseClick: () -> Unit,
  toolbarColor: Color? = null,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = toolbarColor ?: MaterialTheme.colorScheme.surface
    ),
    navigationIcon = {
      IconButtons.IconButton(onClick = onCloseClick) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_x_24),
          contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
        )
      }
    },
    title = {
      Text(text = title)
    },
    windowInsets = windowInsets
  )
}

@PreviewLightDark
@Composable
fun ActionModeTopBarPreview() {
  Previews.Preview {
    ActionModeTopBar(
      title = "1 selected",
      onCloseClick = {}
    )
  }
}

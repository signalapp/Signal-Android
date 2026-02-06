/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * A View wrapper for [ActionModeTopBar] so that we can use the same UI element in View and Compose land.
 */
class ActionModeTopBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  var title by mutableStateOf("")
  var onCloseClick: () -> Unit by mutableStateOf({})

  @Composable
  override fun Content() {
    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(context)) {
      Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
      ) {
        ActionModeTopBar(
          title = title,
          toolbarColor = Color.Transparent,
          onCloseClick = onCloseClick,
          windowInsets = WindowInsets()
        )
      }
    }
  }
}

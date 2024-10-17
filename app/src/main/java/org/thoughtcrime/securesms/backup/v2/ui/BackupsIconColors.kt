/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

sealed interface BackupsIconColors {
  @get:Composable
  val foreground: Color

  @get:Composable
  val background: Color

  data object None : BackupsIconColors {
    override val foreground: Color @Composable get() = error("No coloring should be applied.")
    override val background: Color @Composable get() = error("No coloring should be applied.")
  }

  data object Normal : BackupsIconColors {
    override val foreground: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    override val background: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
  }

  data object Success : BackupsIconColors {
    override val foreground: Color @Composable get() = MaterialTheme.colorScheme.primary
    override val background: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
  }

  data object Warning : BackupsIconColors {
    override val foreground: Color @Composable get() = Color(0xFFFF9500)
    override val background: Color @Composable get() = Color(0xFFF9E4B6)
  }

  data object Error : BackupsIconColors {
    override val foreground: Color @Composable get() = MaterialTheme.colorScheme.error
    override val background: Color @Composable get() = Color(0xFFFFD9D9)
  }
}

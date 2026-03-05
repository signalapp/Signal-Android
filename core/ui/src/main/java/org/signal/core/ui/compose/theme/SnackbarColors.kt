/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Borrowed from [androidx.compose.material3.Snackbar]
 *
 * Works in conjunction with [org.signal.core.ui.Snackbars] for properly
 * themed snackbars in light and dark modes.
 */
@Immutable
data class SnackbarColors(
  val color: Color,
  val contentColor: Color,
  val actionColor: Color,
  val actionContentColor: Color,
  val dismissActionContentColor: Color
)

val LocalSnackbarColors = staticCompositionLocalOf {
  SnackbarColors(
    color = Color.Unspecified,
    contentColor = Color.Unspecified,
    actionColor = Color.Unspecified,
    actionContentColor = Color.Unspecified,
    dismissActionContentColor = Color.Unspecified
  )
}

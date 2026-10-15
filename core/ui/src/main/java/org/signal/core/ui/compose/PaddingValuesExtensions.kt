/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/** Adds [other] to this on every side. */
@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
  val layoutDirection = LocalLayoutDirection.current
  return PaddingValues(
    start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection),
    top = calculateTopPadding() + other.calculateTopPadding(),
    end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection),
    bottom = calculateBottomPadding() + other.calculateBottomPadding()
  )
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp

/**
 * Applies sensible horizontal padding to the given component.
 */
@Composable
fun Modifier.horizontalGutters(
  gutterSize: Dp = dimensionResource(R.dimen.core_ui__gutter)
): Modifier {
  return padding(horizontal = gutterSize)
}

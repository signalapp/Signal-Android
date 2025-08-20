/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.signal.core.ui.R

/**
 * Applies sensible horizontal padding to the given component.
 */
@Composable
fun Modifier.horizontalGutters(
  gutterSize: Dp = dimensionResource(R.dimen.gutter)
): Modifier {
  return padding(horizontal = gutterSize)
}

/**
 * Configures a component to be clickable within its bounds and show a default indication when pressed.
 *
 * This modifier is designed for use on container components, making it easier to create a clickable container with proper accessibility configuration.
 */
@Composable
fun Modifier.clickableContainer(
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  indication: Indication = ripple(bounded = false),
  enabled: Boolean = true,
  contentDescription: String?,
  onClickLabel: String,
  role: Role? = null,
  onClick: () -> Unit
): Modifier = clickable(
  interactionSource = interactionSource,
  indication = indication,
  enabled = enabled,
  onClickLabel = onClickLabel,
  role = role,
  onClick = onClick
).then(
  if (contentDescription != null) {
    Modifier.semantics(mergeDescendants = true) {
      this.contentDescription = contentDescription
    }
  } else {
    Modifier
  }
)

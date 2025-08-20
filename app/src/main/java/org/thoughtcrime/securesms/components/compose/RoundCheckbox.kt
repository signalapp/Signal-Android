/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

/**
 * A custom circular [Checkbox] that can be toggled between checked an unchecked states.
 *
 * @param checked Indicates whether the checkbox is checked or not.
 * @param onCheckedChange A callback function invoked when this checkbox is clicked.
 * @param modifier The [Modifier] to be applied to this checkbox.
 */
@Composable
fun RoundCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val contentDescription = if (checked) {
    stringResource(R.string.SignalCheckbox_accessibility_checked_description)
  } else {
    stringResource(R.string.SignalCheckbox_accessibility_unchecked_description)
  }

  Box(
    modifier = modifier
      .padding(12.dp)
      .size(24.dp)
      .aspectRatio(1f)
      .border(
        width = 1.5.dp,
        color = if (checked) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.outline
        },
        shape = CircleShape
      )
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onCheckedChange(!checked) },
        onClickLabel = stringResource(R.string.SignalCheckbox_accessibility_on_click_label)
      )
      .semantics(mergeDescendants = true) {
        this.role = Role.Checkbox
        this.contentDescription = contentDescription
      }
  ) {
    AnimatedVisibility(
      visible = checked,
      enter = fadeIn(animationSpec = tween(durationMillis = 150)) + scaleIn(initialScale = 1.20f, animationSpec = tween(durationMillis = 500)),
      exit = fadeOut(animationSpec = tween(durationMillis = 300)) + scaleOut(targetScale = 0.50f, animationSpec = tween(durationMillis = 600))
    ) {
      Image(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_check_circle_solid_24),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@SignalPreview
@Composable
private fun RoundCheckboxCheckedPreview() = SignalTheme {
  RoundCheckbox(checked = true, onCheckedChange = {})
}

@SignalPreview
@Composable
private fun RoundCheckboxUncheckedPreview() = SignalTheme {
  RoundCheckbox(checked = false, onCheckedChange = {})
}

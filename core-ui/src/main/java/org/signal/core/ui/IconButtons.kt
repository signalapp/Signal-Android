/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.copied.androidx.compose.material3.IconButtonColors
import org.signal.core.ui.copied.androidx.compose.material3.IconToggleButtonColors

object IconButtons {

  @Composable
  fun iconButtonColors(
    containerColor: Color = Color.Transparent,
    contentColor: Color = LocalContentColor.current,
    disabledContainerColor: Color = Color.Transparent,
    disabledContentColor: Color =
      contentColor.copy(alpha = 0.38f)
  ): IconButtonColors =
    IconButtonColors(
      containerColor = containerColor,
      contentColor = contentColor,
      disabledContainerColor = disabledContainerColor,
      disabledContentColor = disabledContentColor
    )

  @Composable
  fun iconToggleButtonColors(
    containerColor: Color = Color.Transparent,
    contentColor: Color = LocalContentColor.current,
    disabledContainerColor: Color = Color.Transparent,
    disabledContentColor: Color =
      contentColor.copy(alpha = 0.38f),
    checkedContainerColor: Color = Color.Transparent,
    checkedContentColor: Color = MaterialTheme.colorScheme.primary
  ): IconToggleButtonColors =
    IconToggleButtonColors(
      containerColor = containerColor,
      contentColor = contentColor,
      disabledContainerColor = disabledContainerColor,
      disabledContentColor = disabledContentColor,
      checkedContainerColor = checkedContainerColor,
      checkedContentColor = checkedContentColor
    )

  @Composable
  fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    colors: IconButtonColors = iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
  ) {
    Box(
      modifier = modifier
        .minimumInteractiveComponentSize()
        .size(size)
        .clip(shape)
        .background(color = colors.containerColor(enabled).value)
        .clickable(
          onClick = onClick,
          enabled = enabled,
          role = Role.Button,
          interactionSource = interactionSource,
          indication = ripple(
            bounded = false,
            radius = size / 2
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      val contentColor = colors.contentColor(enabled).value
      CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
  }

  @Composable
  fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = iconToggleButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
  ) {
    @Suppress("DEPRECATION_ERROR")
    (
      Box(
        modifier = modifier
          .minimumInteractiveComponentSize()
          .size(size)
          .clip(shape)
          .background(color = colors.containerColor(enabled, checked).value)
          .toggleable(
            value = checked,
            onValueChange = onCheckedChange,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = interactionSource,
            indication = ripple(
              bounded = false,
              radius = size / 2
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        val contentColor = colors.contentColor(enabled, checked).value
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
      }
      )
  }
}

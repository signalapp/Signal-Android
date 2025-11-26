/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.signal.core.ui.R
import org.signal.core.ui.compose.copied.androidx.compose.material3.DropdownMenu
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Properly styled dropdown menus and items.
 */
object DropdownMenus {
  /**
   * Properly styled dropdown menu
   */
  @Composable
  fun Menu(
    modifier: Modifier = Modifier,
    controller: MenuController = remember { MenuController() },
    offsetX: Dp = dimensionResource(id = R.dimen.gutter),
    offsetY: Dp = 0.dp,
    content: @Composable ColumnScope.(MenuController) -> Unit
  ) {
    MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(18.dp))) {
      DropdownMenu(
        expanded = controller.isShown(),
        onDismissRequest = controller::hide,
        offset = DpOffset(
          x = offsetX,
          y = offsetY
        ),
        content = { content(controller) },
        modifier = modifier
          .background(SignalTheme.colors.colorSurface2)
          .widthIn(min = 220.dp)
      )
    }
  }

  /**
   * Properly styled dropdown menu item
   */
  @Composable
  fun Item(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    text: @Composable () -> Unit,
    onClick: () -> Unit
  ) {
    DropdownMenuItem(
      contentPadding = contentPadding,
      text = {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
          text()
        }
      },
      onClick = onClick,
      modifier = modifier
    )
  }

  /**
   * Menu controller to hold menu display state and allow other components
   * to show and hide it.
   */
  class MenuController {
    private var isMenuShown by mutableStateOf(false)

    fun show() {
      isMenuShown = true
    }

    fun hide() {
      isMenuShown = false
    }

    fun toggle() {
      if (isShown()) {
        hide()
      } else {
        show()
      }
    }

    fun isShown() = isMenuShown
  }
}

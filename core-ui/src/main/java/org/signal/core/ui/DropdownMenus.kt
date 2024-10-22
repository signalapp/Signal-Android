/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.signal.core.ui.copied.androidx.compose.material3.DropdownMenu

/**
 * Properly styled dropdown menus and items.
 */
object DropdownMenus {
  /**
   * Properly styled dropdown menu
   */
  @Composable
  fun Menu(
    controller: MenuController = remember { MenuController() },
    modifier: Modifier = Modifier,
    offsetX: Dp = dimensionResource(id = R.dimen.core_ui__gutter),
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
      )
    }
  }

  /**
   * Properly styled dropdown menu item
   */
  @Composable
  fun Item(
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
  ) {
    DropdownMenuItem(
      contentPadding = contentPadding,
      text = text,
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

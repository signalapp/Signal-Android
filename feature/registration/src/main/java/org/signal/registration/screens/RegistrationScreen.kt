/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Previews

/**
 * Scaffold for registration flow screens.
 */
@Composable
fun RegistrationScreen(
  content: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null
) {
  SubcomposeLayout(modifier = modifier.imePadding()) { constraints ->
    val footerPlaceables = footer?.let {
      subcompose("footer", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0

    val headerPlaceables = header?.let {
      subcompose("header", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0

    val contentHeight = (constraints.maxHeight - footerHeight - headerHeight).coerceAtLeast(0)
    val contentPlaceables = subcompose("content", content).map { m ->
      m.measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
      headerPlaceables.forEach { it.placeRelative(0, 0) }
      contentPlaceables.forEach { it.placeRelative(0, headerHeight) }
      footerPlaceables.forEach { it.placeRelative(0, contentHeight + headerHeight) }
    }
  }
}

@Preview
@Composable
private fun RegistrationScreenPreview() {
  Previews.Preview {
    RegistrationScreen(
      content = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        )
      },
      footer = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(color = Color.Green)
        )
      },
      modifier = Modifier.fillMaxSize()
    )
  }
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object BottomSheets {

  /**
   * A modal bottom sheet. Provides [LocalBottomSheetHostState] to [content], so content written for
   * [ComposeBottomSheetDialogFragment] can follow, expand and dismiss this sheet too. [BottomSheetHostState.dismiss]
   * slides the sheet down before calling [onDismissRequest].
   */
  @Composable
  fun BottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    dragHandle: @Composable (() -> Unit)? = { Handle() },
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit
  ) {
    val scope = rememberCoroutineScope()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val hostState = remember(sheetState) {
      BottomSheetHostState(
        onExpand = { scope.launch { sheetState.expand() } },
        onDismiss = {
          scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
              currentOnDismissRequest()
            }
          }
        }
      )
    }

    val isExpanded = sheetState.currentValue == SheetValue.Expanded
    SideEffect {
      hostState.isExpanded = isExpanded
    }

    return ModalBottomSheet(
      onDismissRequest = onDismissRequest,
      sheetState = sheetState,
      properties = properties,
      dragHandle = dragHandle,
      containerColor = containerColor,
      contentWindowInsets = contentWindowInsets
    ) {
      CompositionLocalProvider(LocalBottomSheetHostState provides hostState) {
        Column(modifier = Modifier.onGloballyPositioned(hostState::updateOverflow)) {
          content()
        }
      }
    }
  }

  /**
   * Handle for bottom sheets
   */
  @Composable
  fun Handle(modifier: Modifier = Modifier) {
    Box(
      modifier = modifier
        .size(width = 48.dp, height = 22.dp)
        .padding(vertical = 10.dp)
        .clip(RoundedCornerShape(1000.dp))
        .background(MaterialTheme.colorScheme.outline)
    )
  }
}

@Preview
@Composable
private fun HandlePreview() {
  Previews.Preview {
    BottomSheets.Handle()
  }
}

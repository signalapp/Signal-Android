/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

object BottomSheets {

  @Composable
  fun BottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit
  ) {
    return ModalBottomSheet(
      onDismissRequest = onDismissRequest,
      sheetState = sheetState,
      dragHandle = { Handle() }
    ) {
      content()
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

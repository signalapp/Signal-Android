/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.ImageController
import org.signal.mediasend.screens.edit.MediaEditControl

@Composable
internal fun ImageEditorClearAllButton(
  imageEditorController: ImageController?,
  faded: Boolean,
  modifier: Modifier = Modifier,
  canUndo: Boolean = imageEditorController?.imageEditorState?.undoAvailable ?: false
) {
  MediaEditControl(
    visible = imageEditorController != null && imageEditorController.isUserInEdit && canUndo,
    faded = faded,
    modifier = modifier
  ) {
    Buttons.MediumTonal(
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      onClick = {
        imageEditorController?.clearAllEdits()
      }
    ) {
      Text(text = stringResource(R.string.ImageEditorClearAllButton__clear_all))
    }
  }
}

@DayNightPreviews
@Composable
private fun ImageEditorClearAllButtonPreview() {
  Previews.Preview {
    Box(modifier = Modifier.fillMaxSize()) {
      ImageEditorClearAllButton(
        imageEditorController = remember { ImageController(EditorModel.create(0x0)).apply { enterDrawMode() } },
        faded = false,
        canUndo = true
      )
    }
  }
}

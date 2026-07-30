/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.image

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.R
import org.signal.mediasend.edit.ImageController

@Composable
internal fun ImageEditorUndoRedoButtons(
  imageEditorController: ImageController?,
  modifier: Modifier = Modifier,
  canUndo: Boolean = imageEditorController?.imageEditorState?.undoAvailable ?: false,
  canRedo: Boolean = imageEditorController?.imageEditorState?.redoAvailable ?: false
) {
  ImageEditorGestureAwareControl(imageEditorController, modifier = modifier, extraCheck = {
    canUndo || canRedo
  }) {
    Row(horizontalArrangement = spacedBy(8.dp)) {
      IconButton(
        enabled = canUndo,
        onClick = {
          imageEditorController?.undo()
        },
        colors = IconButtonDefaults.iconButtonColors(containerColor = SignalTheme.colors.colorSurface5)
      ) {
        Icon(
          imageVector = SignalIcons.Undo.imageVector,
          contentDescription = stringResource(R.string.ImageEditorUndoRedoButtons__undo_edit)
        )
      }

      IconButton(
        enabled = canRedo,
        onClick = {
          imageEditorController?.redo()
        },
        colors = IconButtonDefaults.iconButtonColors(containerColor = SignalTheme.colors.colorSurface5)
      ) {
        Icon(
          imageVector = SignalIcons.Redo.imageVector,
          contentDescription = stringResource(R.string.ImageEditorUndoRedoButtons__redo_edit)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun ImageEditorUndoRedoButtonsPreview() {
  Previews.Preview {
    ImageEditorUndoRedoButtons(
      imageEditorController = remember {
        ImageController(EditorModel.create(0x0)).apply {
          enterDrawMode()
        }
      },
      canUndo = true,
      canRedo = true
    )
  }
}

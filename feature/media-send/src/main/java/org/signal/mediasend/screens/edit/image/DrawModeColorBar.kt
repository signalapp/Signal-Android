/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.ImageController

/**
 * Color bar that allows the user to change between highlighter and brush.
 */
@Composable
internal fun DrawModeColorBar(
  imageEditorController: ImageController
) {
  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    HSVColorBar(
      state = imageEditorController.drawColorBarState,
      onColorChanged = imageEditorController::setDrawColor,
      orientation = ColorBarOrientation.HORIZONTAL,
      modifier = Modifier.weight(1f, fill = false)
    )

    IconButtons.IconButton(
      onClick = {
        if (imageEditorController.mode == ImageController.Mode.DRAW) {
          imageEditorController.enterHighlightMode()
        } else {
          imageEditorController.enterDrawMode()
        }
      },
      colors = IconButtons.iconButtonColors(
        containerColor = SignalTheme.colors.colorSurface5
      ),
      size = 40.dp
    ) {
      Icon(
        imageVector = if (imageEditorController.mode == ImageController.Mode.DRAW) {
          SignalIcons.BrushHighlighter.imageVector
        } else {
          SignalIcons.Draw.imageVector
        },
        contentDescription = stringResource(
          if (imageEditorController.mode == ImageController.Mode.DRAW) {
            R.string.DrawModeColorBar__highlighter
          } else {
            R.string.DrawModeColorBar__brush
          }
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun DrawModeColorBarPreview() {
  Previews.Preview {
    DrawModeColorBar(
      imageEditorController = remember {
        ImageController(
          editorModel = EditorModel.create(0),
          brushWidths = BrushWidthsState()
        )
      }
    )
  }
}

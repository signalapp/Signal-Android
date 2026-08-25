/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
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
 * Color bar for the text element being edited or moved, with a toggle that cycles its style. Laid out along whichever
 * axis its host slot gives the bar, so the toggle sits after the bar in both.
 */
@Composable
internal fun TextModeColorBar(
  imageEditorController: ImageController,
  modifier: Modifier = Modifier,
  orientation: ColorBarOrientation = rememberDefaultColorBarOrientation()
) {
  when (orientation) {
    ColorBarOrientation.HORIZONTAL -> {
      Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextColorBar(imageEditorController, orientation, Modifier.weight(1f, fill = false))
        TextStyleToggle(imageEditorController)
      }
    }

    ColorBarOrientation.VERTICAL -> {
      Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        TextColorBar(imageEditorController, orientation, Modifier.weight(1f, fill = false))
        TextStyleToggle(imageEditorController)
      }
    }
  }
}

@Composable
private fun TextColorBar(
  imageEditorController: ImageController,
  orientation: ColorBarOrientation,
  modifier: Modifier = Modifier
) {
  HSVColorBar(
    state = imageEditorController.textColorBarState,
    onColorChanged = imageEditorController::setTextColor,
    orientation = orientation,
    modifier = modifier
  )
}

@Composable
private fun TextStyleToggle(imageEditorController: ImageController) {
  IconButtons.IconButton(
    onClick = imageEditorController::nextTextStyle,
    colors = IconButtons.iconButtonColors(
      containerColor = SignalTheme.colors.colorSurface5
    ),
    size = 40.dp
  ) {
    Icon(
      imageVector = SignalIcons.TextSquare.imageVector,
      contentDescription = stringResource(R.string.TextModeColorBar__toggle_between_text_styles)
    )
  }
}

@DayNightPreviews
@Composable
private fun TextModeColorBarPreview() {
  Previews.Preview {
    TextModeColorBar(
      imageEditorController = remember {
        ImageController(EditorModel.create(0))
      },
      orientation = ColorBarOrientation.HORIZONTAL
    )
  }
}

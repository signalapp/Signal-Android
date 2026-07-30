/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.image

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.FoldablePortraitDayPreview
import org.signal.core.ui.compose.FoldablePortraitNightPreview
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.IconButtons.iconToggleButtonColors
import org.signal.core.ui.compose.PhonePortraitDayPreview
import org.signal.core.ui.compose.PhonePortraitNightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.next
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.edit.ImageController
import org.signal.mediasend.edit.MediaEditScreenDialogs
import org.signal.mediasend.edit.MediaEditScreenEvent
import org.signal.mediasend.edit.MediaEditorToolbar
import org.signal.mediasend.edit.MediaEditorToolbarButton
import org.signal.mediasend.edit.MediaEditorToolbarSharedButtons
import org.signal.mediasend.rememberPreviewState
import java.util.EnumMap

@Composable
fun ImageEditorToolbar(
  imageEditorController: ImageController,
  state: MediaSendState,
  onEvent: (MediaEditScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    imageEditorController.shouldDisplayTextColorBar -> {
      HSVColorBar(
        state = imageEditorController.textColorBarState,
        onColorChanged = imageEditorController::setTextColor,
        modifier = modifier
      )
    }
    imageEditorController.mode == ImageController.Mode.NONE -> {
      ImageEditorNoneStateToolbar(imageEditorController, state, onEvent, modifier)
    }
    imageEditorController.mode == ImageController.Mode.CROP -> {
      ImageEditorCropAndResizeToolbar(imageEditorController, modifier)
    }
    else -> {
      ImageEditorDrawStateToolbar(imageEditorController, modifier)
    }
  }
}

/**
 * Allows user to perform actions while viewing an editable image.
 */
@Composable
private fun ImageEditorNoneStateToolbar(
  imageEditorController: ImageController,
  state: MediaSendState,
  onEvent: (MediaEditScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  MediaEditorToolbar(modifier) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.BrushPen.imageVector,
      onClick = imageEditorController::beginDrawEdit
    )

    MediaEditorToolbarButton(
      imageVector = SignalIcons.CropRotate.imageVector,
      onClick = imageEditorController::beginCropAndRotateEdit
    )

    MediaEditorToolbarSharedButtons(
      state = state,
      canSave = true,
      onEvent = onEvent
    )
  }
}

@Composable
private fun ImageEditorDrawStateToolbar(
  imageEditorController: ImageController,
  modifier: Modifier = Modifier
) {
  MediaEditorToolbar(
    modifier = modifier,
    leading = {
      CommitButton(imageEditorController)
    },
    trailing = {
      DiscardButton(imageEditorController)
    }
  ) {
    ImageEditorToggleButton(
      imageVector = SignalIcons.Draw.imageVector,
      checked = imageEditorController.isUserDrawing,
      onCheckChanged = {
        if (!imageEditorController.isUserDrawing) {
          imageEditorController.enterDrawMode()
        }
      }
    )

    ImageEditorToggleButton(
      imageVector = SignalIcons.Text.imageVector,
      checked = imageEditorController.isUserEnteringText,
      onCheckChanged = {
        if (!imageEditorController.isUserEnteringText) {
          imageEditorController.enterTextMode()
        }
      }
    )

    ImageEditorToggleButton(
      imageVector = SignalIcons.Sticker.imageVector,
      checked = imageEditorController.isUserInsertingSticker,
      onCheckChanged = {
        if (!imageEditorController.isUserInsertingSticker) {
          imageEditorController.enterStickerMode()
        }
      }
    )

    ImageEditorToggleButton(
      imageVector = SignalIcons.Blur.imageVector,
      checked = imageEditorController.isUserBlurring,
      onCheckChanged = {
        if (!imageEditorController.isUserBlurring) {
          imageEditorController.enterBlurMode()
        }
      }
    )
  }
}

@Composable
private fun ImageEditorCropAndResizeToolbar(
  imageEditorController: ImageController,
  modifier: Modifier = Modifier
) {
  MediaEditorToolbar(
    modifier = modifier,
    leading = {
      CommitButton(imageEditorController)
    },
    trailing = {
      DiscardButton(imageEditorController)
    }
  ) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.CropRotate.imageVector,
      onClick = imageEditorController::rotate
    )

    MediaEditorToolbarButton(
      imageVector = SignalIcons.Flip.imageVector,
      onClick = imageEditorController::flip
    )

    val cropLockImageVector = SignalIcons.CropLock.imageVector
    val cropUnlockImageVector = SignalIcons.CropUnlock.imageVector

    IconCrossfadeToggleButton(
      target = if (imageEditorController.isCropAspectRatioLocked) CropLock.LOCKED else CropLock.UNLOCKED,
      setTarget = { target ->
        when (target) {
          CropLock.LOCKED -> imageEditorController.lockCrop()
          CropLock.UNLOCKED -> imageEditorController.unlockCrop()
        }
      },
      targetToImageMap = remember(cropLockImageVector, cropUnlockImageVector) {
        EnumMap<CropLock, ImageVector>(
          CropLock::class.java
        ).apply {
          put(CropLock.LOCKED, cropLockImageVector)
          put(CropLock.UNLOCKED, cropUnlockImageVector)
        }
      }
    )
  }
}

@Composable
private fun CommitButton(imageEditorController: ImageController) {
  MediaEditorToolbarButton(
    imageVector = SignalIcons.Check.imageVector,
    onClick = imageEditorController::commitEdit,
    colors = IconButtons.iconButtonColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  )
}

@Composable
private fun DiscardButton(imageEditorController: ImageController) {
  if (imageEditorController.showDiscardDialog) {
    MediaEditScreenDialogs.DiscardEditsConfirmationDialog(
      onDiscard = imageEditorController::confirmDiscardEdit,
      onDismiss = imageEditorController::dismissDiscardDialog
    )
  }

  MediaEditorToolbarButton(
    imageVector = SignalIcons.X.imageVector,
    onClick = imageEditorController::requestCancelEdit,
    colors = IconButtons.iconButtonColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  )
}

@Composable
private inline fun <reified E : Enum<E>> IconCrossfadeToggleButton(
  target: E,
  crossinline setTarget: (E) -> Unit,
  targetToImageMap: EnumMap<E, ImageVector>
) {
  IconButtons.IconButton(
    onClick = { setTarget(target.next()) }
  ) {
    Crossfade(target) { enumValue ->
      Icon(
        imageVector = targetToImageMap[enumValue]!!,
        contentDescription = null, // TODO
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

@Composable
private fun ImageEditorToggleButton(
  imageVector: ImageVector,
  checked: Boolean,
  onCheckChanged: (Boolean) -> Unit,
  contentDescription: String? = null
) {
  IconButtons.IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckChanged,
    colors = iconToggleButtonColors(
      checkedContentColor = MaterialTheme.colorScheme.onSurface,
      checkedContainerColor = SignalTheme.colors.colorTransparentInverse2
    )
  ) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
  }
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@FoldablePortraitDayPreview
@FoldablePortraitNightPreview
@Composable
private fun ImageEditorNoneStateToolbarPreview() {
  Previews.Preview {
    ImageEditorNoneStateToolbar(
      imageEditorController = remember {
        ImageController(EditorModel.create(0))
      },
      state = rememberPreviewState(),
      onEvent = {}
    )
  }
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@FoldablePortraitDayPreview
@FoldablePortraitNightPreview
@Composable
private fun ImageEditorDrawStateToolbarPreview() {
  Previews.Preview {
    ImageEditorDrawStateToolbar(
      imageEditorController = remember {
        ImageController(EditorModel.create(0)).apply {
          enterDrawMode()
        }
      }
    )
  }
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@FoldablePortraitDayPreview
@FoldablePortraitNightPreview
@Composable
private fun ImageEditorCropAndResizeToolbarPreview() {
  Previews.Preview {
    ImageEditorCropAndResizeToolbar(
      imageEditorController = remember {
        ImageController(EditorModel.create(0)).apply {
          enterCropMode()
        }
      }
    )
  }
}

private enum class CropLock {
  LOCKED, UNLOCKED
}

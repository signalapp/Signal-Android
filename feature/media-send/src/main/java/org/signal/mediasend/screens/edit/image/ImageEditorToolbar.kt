/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import org.signal.mediasend.EditorState
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.ImageController
import org.signal.mediasend.screens.edit.MediaEditScreenDialogs
import org.signal.mediasend.screens.edit.MediaEditScreenEvents
import org.signal.mediasend.screens.edit.MediaEditState
import org.signal.mediasend.screens.edit.MediaEditorToolbar
import org.signal.mediasend.screens.edit.MediaEditorToolbarButton
import org.signal.mediasend.screens.edit.MediaEditorToolbarSharedButtons
import java.util.EnumMap

@Composable
internal fun ImageEditorToolbar(
  imageEditorController: ImageController,
  state: MediaEditState,
  editorState: EditorState.Image,
  onEvent: (MediaEditScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    imageEditorController.shouldDisplayTextColorBar -> {
      TextModeColorBar(
        imageEditorController = imageEditorController,
        modifier = modifier
      )
    }
    imageEditorController.mode == ImageController.Mode.NONE -> {
      ImageEditorNoneStateToolbar(imageEditorController, state, editorState, onEvent, modifier)
    }
    imageEditorController.mode == ImageController.Mode.CROP -> {
      ImageEditorCropAndResizeToolbar(imageEditorController, modifier)
    }
    else -> {
      ImageEditorDrawStateToolbar(imageEditorController, onEvent, modifier)
    }
  }
}

/**
 * Allows user to perform actions while viewing an editable image.
 */
@Composable
private fun ImageEditorNoneStateToolbar(
  imageEditorController: ImageController,
  state: MediaEditState,
  editorState: EditorState.Image,
  onEvent: (MediaEditScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  MediaEditorToolbar(modifier) {
    MediaEditorToolbarButton(
      imageVector = SignalIcons.CropRotate.imageVector,
      onClick = imageEditorController::beginCropAndRotateEdit
    )

    MediaEditorToolbarButton(
      imageVector = SignalIcons.BrushPen.imageVector,
      onClick = imageEditorController::beginDrawEdit
    )

    MediaEditorToolbarSharedButtons(
      state = state,
      editorState = editorState,
      onEvent = onEvent
    )
  }
}

@Composable
private fun ImageEditorDrawStateToolbar(
  imageEditorController: ImageController,
  onEvent: (MediaEditScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  MediaEditorToolbar(
    modifier = modifier,
    leading = {
      DiscardButton(imageEditorController)
    },
    trailing = {
      CommitButton(imageEditorController)
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
        // Unconditional: if a previous pick never delivered a result the mode is still INSERT_STICKER, and gating on it
        // would leave the button dead.
        imageEditorController.enterStickerMode()
        onEvent(MediaEditScreenEvents.StickerClick)
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
      DiscardButton(imageEditorController)
    },
    trailing = {
      CommitButton(imageEditorController)
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
    val cropLockContentDescription = stringResource(R.string.ImageEditorToolbar__aspect_ratio_locked)
    val cropUnlockContentDescription = stringResource(R.string.ImageEditorToolbar__aspect_ratio_unlocked)

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
      },
      targetToContentDescriptionMap = remember(cropLockContentDescription, cropUnlockContentDescription) {
        EnumMap<CropLock, String>(
          CropLock::class.java
        ).apply {
          put(CropLock.LOCKED, cropLockContentDescription)
          put(CropLock.UNLOCKED, cropUnlockContentDescription)
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
  targetToImageMap: EnumMap<E, ImageVector>,
  targetToContentDescriptionMap: EnumMap<E, String>
) {
  IconButtons.IconButton(
    onClick = { setTarget(target.next()) }
  ) {
    Crossfade(target) { enumValue ->
      Icon(
        imageVector = targetToImageMap[enumValue]!!,
        contentDescription = targetToContentDescriptionMap[enumValue],
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
    Box(modifier = Modifier.fillMaxSize()) {
      ImageEditorNoneStateToolbar(
        imageEditorController = remember {
          ImageController(EditorModel.create(0))
        },
        state = remember { MediaEditState() },
        editorState = remember { EditorState.Image(EditorModel.create(0)) },
        onEvent = {}
      )
    }
  }
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@FoldablePortraitDayPreview
@FoldablePortraitNightPreview
@Composable
private fun ImageEditorDrawStateToolbarPreview() {
  Previews.Preview {
    Box(modifier = Modifier.fillMaxSize()) {
      ImageEditorDrawStateToolbar(
        imageEditorController = remember {
          ImageController(EditorModel.create(0)).apply {
            enterDrawMode()
          }
        },
        onEvent = {}
      )
    }
  }
}

@PhonePortraitDayPreview
@PhonePortraitNightPreview
@FoldablePortraitDayPreview
@FoldablePortraitNightPreview
@Composable
private fun ImageEditorCropAndResizeToolbarPreview() {
  Previews.Preview {
    Box(modifier = Modifier.fillMaxSize()) {
      ImageEditorCropAndResizeToolbar(
        imageEditorController = remember {
          ImageController(EditorModel.create(0)).apply {
            enterCropMode()
          }
        }
      )
    }
  }
}

private enum class CropLock {
  LOCKED, UNLOCKED
}

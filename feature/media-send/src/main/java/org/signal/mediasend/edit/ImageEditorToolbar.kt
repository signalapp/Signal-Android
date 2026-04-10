/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.FoldablePortraitDayPreview
import org.signal.core.ui.compose.FoldablePortraitNightPreview
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.IconButtons.iconToggleButtonColors
import org.signal.core.ui.compose.PhonePortraitDayPreview
import org.signal.core.ui.compose.PhonePortraitNightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.copied.androidx.compose.material3.IconButtonColors
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.next
import org.signal.imageeditor.core.model.EditorModel
import java.util.EnumMap

@Composable
fun ImageEditorToolbar(
  imageEditorController: EditorController.Image,
  modifier: Modifier = Modifier
) {
  when (imageEditorController.mode) {
    EditorController.Image.Mode.NONE -> {
      ImageEditorNoneStateToolbar(imageEditorController, modifier)
    }
    EditorController.Image.Mode.CROP -> {
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
  imageEditorController: EditorController.Image,
  modifier: Modifier = Modifier
) {
  OrientedImageEditorToolbar(modifier) {
    ImageEditorButton(
      imageVector = SignalIcons.BrushPen.imageVector,
      onClick = imageEditorController::beginDrawEdit
    )

    ImageEditorButton(
      imageVector = SignalIcons.CropRotate.imageVector,
      onClick = imageEditorController::beginCropAndRotateEdit
    )

    ImageEditorButton(
      imageVector = SignalIcons.QualityHigh.imageVector,
      onClick = imageEditorController::toggleImageQuality
    )

    ImageEditorButton(
      imageVector = SignalIcons.Save.imageVector,
      onClick = imageEditorController::saveToDisk
    )

    ImageEditorButton(
      imageVector = SignalIcons.Plus.imageVector, // TODO [alex] - wrong art asset
      onClick = imageEditorController::addMedia
    )
  }
}

@Composable
private fun ImageEditorDrawStateToolbar(
  imageEditorController: EditorController.Image,
  modifier: Modifier = Modifier
) {
  OrientedImageEditorToolbar(
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
  imageEditorController: EditorController.Image,
  modifier: Modifier = Modifier
) {
  OrientedImageEditorToolbar(
    modifier = modifier,
    leading = {
      CommitButton(imageEditorController)
    },
    trailing = {
      DiscardButton(imageEditorController)
    }
  ) {
    ImageEditorButton(
      imageVector = SignalIcons.CropRotate.imageVector,
      onClick = imageEditorController::rotate
    )

    ImageEditorButton(
      imageVector = SignalIcons.Flip.imageVector,
      onClick = imageEditorController::flip
    )

    val cropLockImageVector = SignalIcons.CropLock.imageVector
    val cropUnlockImageVector = SignalIcons.CropUnlock.imageVector

    IconCrossfadeToggleButton(
      target = if (imageEditorController.isCropLocked) CropLock.LOCKED else CropLock.UNLOCKED,
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
private fun CommitButton(imageEditorController: EditorController.Image) {
  ImageEditorButton(
    imageVector = SignalIcons.Check.imageVector,
    onClick = imageEditorController::commitEdit,
    colors = IconButtons.iconButtonColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
    )
  )
}

@Composable
private fun DiscardButton(imageEditorController: EditorController.Image) {
  ImageEditorButton(
    imageVector = SignalIcons.X.imageVector,
    onClick = imageEditorController::cancelEdit,
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
private fun ImageEditorButton(
  imageVector: ImageVector,
  onClick: () -> Unit,
  contentDescription: String? = null,
  colors: IconButtonColors = IconButtons.iconButtonColors()
) {
  IconButtons.IconButton(
    onClick = onClick,
    colors = colors
  ) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
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

@Composable
private fun OrientedImageEditorToolbar(
  modifier: Modifier = Modifier,
  leading: @Composable () -> Unit = {},
  trailing: @Composable () -> Unit = {},
  content: @Composable () -> Unit
) {
  val windowBreakpoint = rememberWindowBreakpoint()
  val isRow = windowBreakpoint == WindowBreakpoint.SMALL

  if (isRow) {
    Row(modifier = modifier.height(48.dp)) {
      leading()

      Row(
        modifier = Modifier
          .fillMaxHeight()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      trailing()
    }
  } else {
    Column(modifier = modifier.width(48.dp)) {
      leading()

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
      ) {
        content()
      }

      trailing()
    }
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
        EditorController.Image(EditorModel.create(0))
      }
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
        EditorController.Image(EditorModel.create(0)).apply {
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
        EditorController.Image(EditorModel.create(0)).apply {
          enterCropMode()
        }
      }
    )
  }
}

private enum class CropLock {
  LOCKED, UNLOCKED
}

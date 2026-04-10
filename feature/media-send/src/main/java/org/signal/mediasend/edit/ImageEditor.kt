/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.imageeditor.core.ImageEditorView

@Composable
fun ImageEditor(
  controller: EditorController.Image,
  modifier: Modifier = Modifier
) {
  AndroidView(
    factory = { context -> ImageEditorView(context) },
    update = { view ->
      view.model = controller.editorModel
      view.mode = mapMode(controller.mode)
    },
    onReset = { },
    modifier = modifier.clipToBounds()
  )
}

private fun mapMode(mode: EditorController.Image.Mode): ImageEditorView.Mode {
  return when (mode) {
    EditorController.Image.Mode.NONE -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.CROP -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.TEXT -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.DRAW -> ImageEditorView.Mode.Draw
    EditorController.Image.Mode.HIGHLIGHT -> ImageEditorView.Mode.Draw
    EditorController.Image.Mode.BLUR -> ImageEditorView.Mode.Blur
    EditorController.Image.Mode.MOVE_STICKER -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.MOVE_TEXT -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.DELETE -> ImageEditorView.Mode.MoveAndResize
    EditorController.Image.Mode.INSERT_STICKER -> ImageEditorView.Mode.MoveAndResize
  }
}

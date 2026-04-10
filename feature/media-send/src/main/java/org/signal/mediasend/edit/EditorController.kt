/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.signal.imageeditor.core.model.EditorModel

@Stable
sealed interface EditorController {

  @Stable
  class Container @RememberInComposition constructor() {
    val controllers = SnapshotStateMap<Uri, EditorController>()

    fun getOrCreateImageController(uri: Uri, editorModel: EditorModel): Image {
      return controllers.getOrPut(uri) { Image(editorModel) } as Image
    }
  }

  @Stable
  class Image @RememberInComposition constructor(val editorModel: EditorModel) : EditorController {

    override val isUserInEdit: Boolean
      get() = mode != Mode.NONE

    var mode: Mode by mutableStateOf(Mode.NONE)

    var isCropLocked: Boolean by mutableStateOf(editorModel.isCropAspectLocked)
      private set

    val isUserDrawing: Boolean
      get() = mode == Mode.DRAW || mode == Mode.HIGHLIGHT

    val isUserBlurring: Boolean
      get() = mode == Mode.BLUR

    val isUserEnteringText: Boolean
      get() = mode == Mode.TEXT

    val isUserInsertingSticker: Boolean
      get() = mode == Mode.INSERT_STICKER

    fun beginDrawEdit() {
      enterDrawMode()
    }

    fun beginCropAndRotateEdit() {
      enterCropMode()
    }

    fun cancelEdit() {
      mode = Mode.NONE
    }

    fun commitEdit() {
      mode = Mode.NONE
    }

    fun enterDrawMode() {
      mode = Mode.DRAW
    }

    fun enterHighlightMode() {
      mode = Mode.HIGHLIGHT
    }

    fun enterBlurMode() {
      mode = Mode.BLUR
    }

    fun enterCropMode() {
      mode = Mode.CROP
    }

    fun enterTextMode() {
      mode = Mode.TEXT
    }

    fun enterStickerMode() {
      mode = Mode.INSERT_STICKER
    }

    fun lockCrop() {
      editorModel.setCropAspectLock(true)
      isCropLocked = true
    }

    fun unlockCrop() {
      editorModel.setCropAspectLock(false)
      isCropLocked = false
    }

    fun flip() {
      editorModel.flipHorizontal()
    }

    fun rotate() {
      editorModel.rotate90anticlockwise()
    }

    fun toggleImageQuality() {
      // TODO
    }

    fun saveToDisk() {
      // TODO
    }

    fun addMedia() {
      // TODO
    }

    enum class Mode {
      NONE,
      CROP,
      TEXT,
      DRAW,
      HIGHLIGHT,
      BLUR,
      MOVE_STICKER,
      MOVE_TEXT,
      DELETE,
      INSERT_STICKER
    }
  }

  object VideoTrim : EditorController {
    override val isUserInEdit: Boolean = false
  }

  val isUserInEdit: Boolean
}

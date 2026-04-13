/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.graphics.Paint
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
    private val controllers = SnapshotStateMap<Uri, EditorController>()

    fun getOrCreateImageController(uri: Uri, editorModel: EditorModel): Image {
      return controllers.getOrPut(uri) { Image(editorModel) } as Image
    }
  }

  @Stable
  class Image @RememberInComposition constructor(val editorModel: EditorModel) : EditorController {

    override val isUserInEdit: Boolean
      get() = mode != Mode.NONE

    val imageEditorState = ImageEditorState(editorModel).also {
      it.onGestureCompleted = { drawSessionDirty = true }
    }

    var mode: Mode by mutableStateOf(Mode.NONE)

    var isCropAspectRatioLocked: Boolean by mutableStateOf(editorModel.isCropAspectLocked)
      private set

    val dialRotation: Float
      get() = editorModel.mainImage?.let { Math.toDegrees(it.localRotationAngle.toDouble()).toFloat() } ?: 0f

    private var initialDialScale: Float = editorModel.mainImage?.localScaleX ?: 1f
    private var initialDialImageDegrees: Float = 0f
    private var minDialScaleDown: Float = 1f
    private var drawSessionSnapshot: ByteArray? = null
    private var drawSessionDirty: Boolean = false

    var showDiscardDialog: Boolean by mutableStateOf(false)
      private set

    val hasUnsavedChanges: Boolean
      get() = when {
        mode == Mode.CROP -> imageEditorState.undoAvailable
        isInDrawSession -> drawSessionDirty
        else -> false
      }

    fun requestCancelEdit() {
      if (hasUnsavedChanges) {
        showDiscardDialog = true
      } else {
        cancelEdit()
      }
    }

    fun dismissDiscardDialog() {
      showDiscardDialog = false
    }

    fun confirmDiscardEdit() {
      showDiscardDialog = false
      cancelEdit()
    }

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
      when {
        mode == Mode.CROP -> {
          editorModel.clearUndoStack()
          editorModel.doneCrop()
        }
        isInDrawSession -> {
          drawSessionSnapshot?.let { editorModel.restoreFromSnapshot(it) }
        }
      }
      exitEditMode()
    }

    fun commitEdit() {
      if (mode == Mode.CROP) {
        editorModel.doneCrop()
      }
      exitEditMode()
    }

    private fun exitEditMode() {
      drawSessionSnapshot = null
      drawSessionDirty = false
      mode = Mode.NONE
      imageEditorState.isDrawing = false
      imageEditorState.isBlur = false
    }

    fun enterDrawMode() {
      snapshotIfNewDrawSession()
      mode = Mode.DRAW
      syncDrawingState()
    }

    fun enterHighlightMode() {
      snapshotIfNewDrawSession()
      mode = Mode.HIGHLIGHT
      syncDrawingState()
    }

    fun enterBlurMode() {
      snapshotIfNewDrawSession()
      mode = Mode.BLUR
      syncDrawingState()
    }

    private fun snapshotIfNewDrawSession() {
      if (!isInDrawSession) {
        drawSessionSnapshot = editorModel.createSnapshot()
        drawSessionDirty = false
      }
    }

    fun setDrawColor(color: Int) {
      imageEditorState.drawColor = color
    }

    fun setDrawThickness(thickness: Float) {
      imageEditorState.drawThickness = thickness
    }

    private val isInDrawSession: Boolean
      get() = mode == Mode.DRAW || mode == Mode.HIGHLIGHT || mode == Mode.BLUR

    private fun syncDrawingState() {
      imageEditorState.isDrawing = true
      imageEditorState.isBlur = mode == Mode.BLUR
      imageEditorState.drawCap = if (mode == Mode.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND
    }

    fun enterCropMode() {
      editorModel.startCrop()
      initialDialScale = editorModel.mainImage?.localScaleX ?: 1f
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
      isCropAspectRatioLocked = true
    }

    fun unlockCrop() {
      editorModel.setCropAspectLock(false)
      isCropAspectRatioLocked = false
    }

    fun flip() {
      editorModel.flipHorizontal()
    }

    fun rotate() {
      editorModel.rotate90anticlockwise()
    }

    fun onDialGestureStart() {
      val mainImage = editorModel.mainImage ?: return
      initialDialScale = mainImage.localScaleX
      minDialScaleDown = 1f
      editorModel.pushUndoPoint()
      editorModel.updateUndoRedoAvailabilityState()
      initialDialImageDegrees = Math.toDegrees(mainImage.localRotationAngle.toDouble()).toFloat()
    }

    fun onDialRotationChanged(degrees: Float) {
      editorModel.setMainImageEditorMatrixRotation(degrees - initialDialImageDegrees, minDialScaleDown)
    }

    fun onDialGestureEnd() {
      val mainImage = editorModel.mainImage ?: return
      mainImage.commitEditorMatrix()
      editorModel.postEdit(true)
      initialDialScale = mainImage.localScaleX
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

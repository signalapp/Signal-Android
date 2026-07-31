/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.SelectableRenderer
import org.signal.imageeditor.core.TappableRenderer
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer
import org.signal.mediasend.edit.image.BrushTool
import org.signal.mediasend.edit.image.BrushWidthsState
import org.signal.mediasend.edit.image.HSVColorBarState
import org.signal.mediasend.edit.image.ImageEditorState

/**
 * Holds the editor state for a single image (modes, undo, selection, etc.).
 *
 * Videos have no comparable per-item editor state — no undo stack, no modes — so they are rendered directly based
 * on the focused media type rather than through a controller. Trim data lives in the view-model state, and transient
 * trim/seek interaction flows through the screen's event channel.
 */
@Stable
internal class ImageController(
  private val editorModel: EditorModel,
  private val brushWidths: BrushWidthsState = BrushWidthsState()
) {

  companion object {
    /** Size of a new sticker, relative to the visible area of the image. */
    private const val STICKER_SCALE = 0.4f
  }

  val isUserInEdit: Boolean by derivedStateOf { mode != Mode.NONE }

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
  private var drawSessionDirty: Boolean by mutableStateOf(false)
  private var modeBeforeStickerInsertion: Mode = Mode.NONE
  private var modeBeforeDrag: Mode = Mode.NONE

  var textEditingElement: EditorElement? by mutableStateOf(null)
    private set

  var selectedElement: EditorElement? by mutableStateOf(null)
    private set

  val textColorBarState = HSVColorBarState()
  val drawColorBarState = HSVColorBarState()

  var showDiscardDialog: Boolean by mutableStateOf(false)
    private set

  private val isInDrawSession: Boolean by derivedStateOf { mode == Mode.DRAW || mode == Mode.HIGHLIGHT || mode == Mode.BLUR }

  val hasUnsavedChanges: Boolean by derivedStateOf {
    when {
      mode == Mode.CROP -> imageEditorState.undoAvailable
      mode == Mode.TEXT -> (textEditingElement?.renderer as? MultiLineTextRenderer)?.text?.isNotEmpty() == true
      isInDrawSession -> drawSessionDirty
      else -> false
    }
  }

  val shouldDisplayTextColorBar: Boolean by derivedStateOf {
    textEditingElement != null || mode == Mode.MOVE_TEXT
  }

  val isUserDrawing: Boolean by derivedStateOf { mode == Mode.DRAW || mode == Mode.HIGHLIGHT }
  val isUserBlurring: Boolean by derivedStateOf { mode == Mode.BLUR }

  val brushTool: BrushTool? by derivedStateOf {
    when (mode) {
      Mode.DRAW -> BrushTool.MARKER
      Mode.HIGHLIGHT -> BrushTool.HIGHLIGHTER
      Mode.BLUR -> BrushTool.BLUR
      else -> null
    }
  }

  val brushWidthFraction: Float by derivedStateOf { brushTool?.let { brushWidths[it] } ?: 0f }

  val brushThickness: Float by derivedStateOf { brushTool?.thicknessAt(brushWidthFraction) ?: 0f }

  val isUserEnteringText: Boolean by derivedStateOf { mode == Mode.TEXT }
  val isUserInsertingSticker: Boolean by derivedStateOf { mode == Mode.INSERT_STICKER }

  val isUserDraggingElement: Boolean by derivedStateOf { mode == Mode.DELETE }

  var isDraggedElementOverTrash: Boolean by mutableStateOf(false)
    private set

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

  fun beginDrawEdit() {
    enterDrawMode()
  }

  fun beginCropAndRotateEdit() {
    enterCropMode()
  }

  fun cancelEdit() {
    when {
      mode == Mode.TEXT -> {
        finishTextEditing()
      }
      mode == Mode.CROP -> {
        editorModel.clearUndoStack()
        editorModel.doneCrop()
        exitEditMode()
      }
      isInDrawSession -> {
        drawSessionSnapshot?.let { editorModel.restoreFromSnapshot(it) }
        exitEditMode()
      }
      else -> exitEditMode()
    }
  }

  fun commitEdit() {
    when (mode) {
      Mode.TEXT -> finishTextEditing()
      Mode.CROP -> {
        editorModel.doneCrop()
        exitEditMode()
      }
      else -> exitEditMode()
    }
  }

  private fun exitEditMode() {
    drawSessionSnapshot = null
    drawSessionDirty = false
    selectedElement = null
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

  fun undo() {
    editorModel.undo()
  }

  fun redo() {
    editorModel.redo()
  }

  fun clearAllEdits() {
    while (imageEditorState.undoAvailable) {
      editorModel.undo()
    }
  }

  fun setDrawColor(color: Int) {
    imageEditorState.drawColor = brushTool?.applyAlpha(color) ?: color
  }

  fun setBrushWidthFraction(fraction: Float) {
    val tool = brushTool ?: return
    brushWidths.set(tool, fraction)
    imageEditorState.drawThickness = tool.thicknessAt(fraction)
  }

  private fun syncDrawingState() {
    imageEditorState.isDrawing = true
    imageEditorState.isBlur = mode == Mode.BLUR
    imageEditorState.drawCap = if (mode == Mode.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND
    imageEditorState.drawThickness = brushThickness
    setDrawColor(drawColorBarState.color)
  }

  fun enterCropMode() {
    editorModel.startCrop()
    initialDialScale = editorModel.mainImage?.localScaleX ?: 1f
    mode = Mode.CROP
  }

  fun enterTextMode() {
    snapshotIfNewDrawSession()
    val renderer = MultiLineTextRenderer("", textColorBarState.color, MultiLineTextRenderer.Mode.REGULAR)
    val element = EditorElement(renderer, EditorModel.Z_TEXT)
    editorModel.addElementCentered(element, 1f)
    beginTextEditing(element)
  }

  private fun beginTextEditing(element: EditorElement) {
    mode = Mode.TEXT
    textEditingElement = element
    imageEditorState.textEditingElement = element
    editorModel.addFade()
    editorModel.setSelectionVisible(false)
    (element.renderer as? MultiLineTextRenderer)?.setFocused(true)
  }

  fun finishTextEditing() {
    val element = textEditingElement ?: return
    val renderer = element.renderer as? MultiLineTextRenderer
    val hasText = renderer?.text?.isNotEmpty() == true
    val snapshot = drawSessionSnapshot

    renderer?.setFocused(false)
    editorModel.zoomOut()
    editorModel.removeFade()
    editorModel.setSelectionVisible(true)

    if (!hasText && snapshot != null) {
      editorModel.restoreFromSnapshot(snapshot)
    }

    editorModel.setSelected(null)
    textEditingElement = null
    imageEditorState.textEditingElement = null
    exitEditMode()
  }

  fun onTextChanged(text: String) {
    val element = textEditingElement ?: return
    val renderer = element.renderer as? MultiLineTextRenderer ?: return
    renderer.setText(text)
    imageEditorState.invalidate()
  }

  fun onTextSelectionChanged(selStart: Int, selEnd: Int) {
    val element = textEditingElement ?: return
    val renderer = element.renderer as? MultiLineTextRenderer ?: return
    renderer.setSelection(selStart, selEnd)
    editorModel.zoomToTextElement(element, renderer)
    imageEditorState.invalidate()
  }

  fun setTextColor(color: Int) {
    val element = textEditingElement ?: selectedElement
    val renderer = element?.renderer as? MultiLineTextRenderer ?: return
    renderer.color = color
    imageEditorState.invalidate()
  }

  fun onEntityDown(element: EditorElement?) {
    if (element != null && element.renderer is SelectableRenderer) {
      selectElement(element)
    } else {
      clearSelection()
    }
  }

  fun onEntitySingleTap(element: EditorElement?) {
    val tappable = element?.renderer as? TappableRenderer ?: return

    tappable.onTapped()
    imageEditorState.invalidate()
  }

  private fun selectElement(element: EditorElement) {
    (selectedElement?.renderer as? SelectableRenderer)?.onSelected(false)
    (element.renderer as? SelectableRenderer)?.onSelected(true)
    editorModel.setSelected(element)
    selectedElement = element
    mode = when (element.renderer) {
      is MultiLineTextRenderer -> Mode.MOVE_TEXT
      else -> Mode.MOVE_STICKER
    }
  }

  private fun clearSelection() {
    if (selectedElement != null) {
      (selectedElement?.renderer as? SelectableRenderer)?.onSelected(false)
      editorModel.setSelected(null)
      selectedElement = null
      mode = Mode.NONE
      imageEditorState.invalidate()
    }
  }

  fun enterStickerMode() {
    modeBeforeStickerInsertion = mode
    mode = Mode.INSERT_STICKER
  }

  fun insertSticker(renderer: Renderer) {
    val element = EditorElement(renderer, EditorModel.Z_STICKERS)
    editorModel.addElementCentered(element, STICKER_SCALE)

    // The picker can be opened mid-draw-session, where touches would otherwise keep painting.
    imageEditorState.isDrawing = false
    imageEditorState.isBlur = false

    selectElement(element)
    imageEditorState.invalidate()
  }

  fun cancelStickerInsertion() {
    if (mode == Mode.INSERT_STICKER) {
      mode = modeBeforeStickerInsertion
    }
  }

  /** Reveals the trash so the drag can end in a delete. */
  fun onDragStarted(element: EditorElement?) {
    if (mode == Mode.CROP || element == null || element.renderer !is SelectableRenderer) {
      return
    }

    modeBeforeDrag = mode
    selectElement(element)
    isDraggedElementOverTrash = false
    mode = Mode.DELETE
    setTrashVisible(true)
  }

  fun onDragMoved(element: EditorElement?, isOverTrash: Boolean) {
    if (mode != Mode.DELETE || element == null || isOverTrash == isDraggedElementOverTrash) {
      return
    }

    isDraggedElementOverTrash = isOverTrash

    if (isOverTrash) {
      element.animatePartialFadeOut(imageEditorState::invalidate)
    } else {
      element.animatePartialFadeIn(imageEditorState::invalidate)
    }
  }

  fun onDragEnded(element: EditorElement?, isOverTrash: Boolean) {
    if (mode != Mode.DELETE) {
      return
    }

    isDraggedElementOverTrash = false
    setTrashVisible(false)

    if (element == null) {
      exitEditMode()
      return
    }

    if (isOverTrash) {
      editorModel.delete(element)
      editorModel.updateUndoRedoAvailabilityState()

      when (modeBeforeDrag) {
        Mode.DRAW, Mode.HIGHLIGHT, Mode.BLUR -> {
          selectedElement = null
          mode = modeBeforeDrag
        }
        else -> exitEditMode()
      }
    } else {
      element.animatePartialFadeIn(imageEditorState::invalidate)
      selectElement(element)
    }
  }

  private fun setTrashVisible(visible: Boolean) {
    editorModel.trash.flags
      .setVisible(visible)
      .persist()

    imageEditorState.invalidate()
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
    imageEditorState.isGestureActive = true
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
    imageEditorState.isGestureActive = false
    val mainImage = editorModel.mainImage ?: return
    mainImage.commitEditorMatrix()
    editorModel.postEdit(true)
    initialDialScale = mainImage.localScaleX
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

  /**
   * The controller for each image in the selection. Owned by the view-model rather than the composition so edits from
   * outside the Edit screen apply right away.
   */
  @Stable
  class Container(
    private val brushWidths: BrushWidthsState = BrushWidthsState()
  ) {
    private val controllers = SnapshotStateMap<Uri, ImageController>()

    fun getOrCreate(uri: Uri, editorModel: EditorModel): ImageController {
      val existing = controllers[uri]
      if (existing != null && existing.editorModel === editorModel) {
        return existing
      }

      // Re-adding a removed item builds it a fresh model, leaving any cached controller editing one nobody renders.
      return ImageController(editorModel, brushWidths).also { controllers[uri] = it }
    }

    fun remove(uri: Uri) {
      controllers.remove(uri)
    }
  }
}

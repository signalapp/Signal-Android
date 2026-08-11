/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.content.Context
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.SelectableRenderer
import org.signal.imageeditor.core.TappableRenderer
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.MultiLineTextRenderer
import org.signal.mediasend.screens.edit.image.BrushTool
import org.signal.mediasend.screens.edit.image.BrushWidthsState
import org.signal.mediasend.screens.edit.image.FaceDetectionResult
import org.signal.mediasend.screens.edit.image.HSVColorBarState
import org.signal.mediasend.screens.edit.image.ImageEditorState
import org.signal.mediasend.screens.edit.image.detectFaces

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
    private set

  var isCropAspectRatioLocked: Boolean by mutableStateOf(editorModel.isCropAspectLocked)
    private set

  val dialRotation: Float
    get() = editorModel.mainImage?.let { Math.toDegrees(it.localRotationAngle.toDouble()).toFloat() } ?: 0f

  private var initialDialScale: Float = editorModel.mainImage?.localScaleX ?: 1f
  private var initialDialImageDegrees: Float = 0f
  private var minDialScaleDown: Float = 1f
  private var drawSessionSnapshot: ByteArray? by mutableStateOf(null)
  private var drawSessionDirty: Boolean by mutableStateOf(false)
  private var modeBeforeStickerInsertion: Mode = Mode.NONE

  /**
   * The mode a transient one falls back to. Selecting, dragging, inserting a sticker and entering text are all things
   * the user does *within* an editing session, so they must not be able to strand that session -- or start one.
   */
  private var restingMode: Mode = Mode.NONE

  var textEditingElement: EditorElement? by mutableStateOf(null)
    private set

  var selectedElement: EditorElement? by mutableStateOf(null)
    private set

  val textColorBarState = HSVColorBarState()
  val drawColorBarState = HSVColorBarState()

  var showDiscardDialog: Boolean by mutableStateOf(false)
    private set

  /**
   * Whether there is an uncommitted session to commit or discard. Keyed off the snapshot rather than [mode], because
   * the transient modes -- placing a sticker, entering text, dragging -- all sit on top of a live session.
   */
  private val isInDrawSession: Boolean by derivedStateOf { drawSessionSnapshot != null }

  /** Whether the brush itself is the active tool, which is a narrower question than [isInDrawSession]. */
  private val isPaintMode: Boolean by derivedStateOf { mode == Mode.DRAW || mode == Mode.HIGHLIGHT || mode == Mode.BLUR }

  val hasUnsavedChanges: Boolean by derivedStateOf {
    when {
      mode == Mode.CROP -> imageEditorState.undoAvailable
      mode == Mode.TEXT -> (textEditingElement?.renderer as? MultiLineTextRenderer)?.text?.isNotEmpty() == true
      isInDrawSession -> drawSessionDirty
      else -> false
    }
  }

  val shouldDisplayTextColorBar: Boolean by derivedStateOf {
    textEditingElement != null
  }

  val isUserDrawing: Boolean by derivedStateOf { mode == Mode.DRAW || mode == Mode.HIGHLIGHT }
  val isUserBlurring: Boolean by derivedStateOf { mode == Mode.BLUR }

  var isDetectingFaces: Boolean by mutableStateOf(false)
    private set

  /**
   * Whether the image's faces are masked, which is what the blur-faces toggle reflects. Taken from the model rather than
   * the toggle so that undoing or clearing the masks turns it back off.
   */
  val isBlurringFaces: Boolean by derivedStateOf {
    // Reading the revision is what re-derives this when masks are added to or removed from the model.
    imageEditorState.revision
    isDetectingFaces || editorModel.hasFaceRenderer()
  }

  private var cachedFaceDetection: FaceDetectionResult? = null
  private var isFaceBlurRequested: Boolean = false

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

  var isDraggedElementOverTrash: Boolean by mutableStateOf(false)
    private set

  /** Whether back should back out of an editor mode rather than leaving the screen. */
  val canHandleBack: Boolean by derivedStateOf {
    when (mode) {
      // The sticker picker is its own window and owns back while it is up, and DELETE only exists mid-drag.
      Mode.INSERT_STICKER, Mode.DELETE -> false
      Mode.NONE -> selectedElement != null
      else -> true
    }
  }

  /**
   * Backs out of the current mode into the one before it. Draw and crop sessions go through [requestCancelEdit] so that
   * backing out of a dirty one asks first rather than silently throwing the work away.
   */
  fun onBackPressed() {
    // A selection is a level of its own: back gives that up before it gives up the mode.
    if (selectedElement != null) {
      clearSelection()
      return
    }

    when (mode) {
      Mode.TEXT -> finishTextEditing()
      Mode.DRAW, Mode.HIGHLIGHT, Mode.BLUR, Mode.CROP -> requestCancelEdit()
      Mode.NONE, Mode.INSERT_STICKER, Mode.DELETE -> Unit
    }
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
    transitionTo(Mode.NONE)
    imageEditorState.isDrawing = false
    imageEditorState.isBlur = false
  }

  private fun transitionTo(newMode: Mode) {
    if (!newMode.isTransient) {
      restingMode = newMode
    }

    mode = newMode
  }

  /** Ends a transient mode by returning to the session the user was in before it. */
  private fun returnToRestingMode() {
    when (restingMode) {
      Mode.DRAW, Mode.HIGHLIGHT, Mode.BLUR -> {
        transitionTo(restingMode)
        syncDrawingState()
      }

      // The crop is still open on the model, so there is nothing to restart -- only the mode to hand back.
      Mode.CROP -> transitionTo(Mode.CROP)

      else -> exitEditMode()
    }

    imageEditorState.invalidate()
  }

  fun enterDrawMode() {
    clearSelection()
    snapshotIfNewDrawSession()
    transitionTo(Mode.DRAW)
    syncDrawingState()
  }

  fun enterHighlightMode() {
    clearSelection()
    snapshotIfNewDrawSession()
    transitionTo(Mode.HIGHLIGHT)
    syncDrawingState()
  }

  fun enterBlurMode() {
    clearSelection()
    snapshotIfNewDrawSession()
    transitionTo(Mode.BLUR)
    syncDrawingState()
  }

  private fun snapshotIfNewDrawSession() {
    if (drawSessionSnapshot == null) {
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

  /**
   * Masks every face in the image, finding them first unless a previous detection still describes the image as it is now
   * cropped. Suspends for as long as the detection takes, which [isDetectingFaces] reports so the screen can say so.
   */
  suspend fun blurFaces(context: Context) {
    isFaceBlurRequested = true

    if (isDetectingFaces) {
      return
    }

    val cached = cachedFaceDetection?.takeIf { it.matches(editorModel) }
    if (cached != null) {
      applyFaceBlurs(cached)
      return
    }

    isDetectingFaces = true
    val result = try {
      withContext(Dispatchers.Default) {
        detectFaces(context, editorModel, imageEditorState.typefaceProvider)
      }
    } finally {
      isDetectingFaces = false
    }

    // The toggle can go back off while detection runs, and finding faces after that must not mask them anyway.
    if (isFaceBlurRequested) {
      applyFaceBlurs(result)
    }
  }

  fun clearFaceBlurs() {
    isFaceBlurRequested = false

    if (!editorModel.hasFaceRenderer()) {
      return
    }

    editorModel.clearFaceRenderers()
    drawSessionDirty = true
    imageEditorState.invalidate()
  }

  private fun applyFaceBlurs(result: FaceDetectionResult) {
    if (result.faces.isEmpty()) {
      // Not worth keeping: a re-run once the image has loaded, or after a crop, may well find something.
      cachedFaceDetection = null
      return
    }

    editorModel.addFaceBlurs(result.faces, result.renderSize, result.cropPosition)
    cachedFaceDetection = result
    drawSessionDirty = true
    imageEditorState.invalidate()
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
    // A selected element takes the touch: with the brush live, a sticker the user just placed could not be moved.
    val canPaint = selectedElement == null

    imageEditorState.isDrawing = canPaint
    imageEditorState.isBlur = canPaint && mode == Mode.BLUR
    imageEditorState.drawCap = if (mode == Mode.HIGHLIGHT) Paint.Cap.SQUARE else Paint.Cap.ROUND
    imageEditorState.drawThickness = brushThickness
    setDrawColor(drawColorBarState.color)
  }

  fun enterCropMode() {
    // Two fingers belong to the crop from here, so there would be no way back out of a zoom.
    imageEditorState.clearZoom()
    editorModel.startCrop()
    initialDialScale = editorModel.mainImage?.localScaleX ?: 1f
    transitionTo(Mode.CROP)
  }

  fun enterTextMode() {
    snapshotIfNewDrawSession()
    val renderer = MultiLineTextRenderer("", textColorBarState.color, MultiLineTextRenderer.Mode.REGULAR)
    val element = EditorElement(renderer, EditorModel.Z_TEXT)
    editorModel.addElementCentered(element, 1f)
    beginTextEditing(element)
  }

  private fun beginTextEditing(element: EditorElement) {
    transitionTo(Mode.TEXT)
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

    renderer?.setFocused(false)
    editorModel.zoomOut()
    editorModel.removeFade()
    editorModel.setSelectionVisible(true)

    textEditingElement = null
    imageEditorState.textEditingElement = null

    if (hasText) {
      drawSessionDirty = true

      // Staying selected keeps the brush off it, so it can still be moved or double-tapped back open.
      selectElement(element)
    } else {
      // Drop just the abandoned element. Restoring the draw session snapshot here would take every stroke made before
      // text entry with it.
      clearSelection()
      editorModel.delete(element)
      editorModel.updateUndoRedoAvailabilityState()
    }

    // Returning to the session that text entry was started from keeps its commit and discard available, rather than
    // banking the work without the user ever confirming it.
    returnToRestingMode()
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

  /** Cycles the text element through regular, highlight, underline, and outline. */
  fun nextTextStyle() {
    val element = textEditingElement ?: selectedElement
    val renderer = element?.renderer as? MultiLineTextRenderer ?: return
    renderer.nextMode()
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

  /** Re-opens an existing text element, which is how the user gets back to its color and style controls. */
  fun onEntityDoubleTap(element: EditorElement?) {
    if (mode == Mode.CROP || element == null || element.renderer !is MultiLineTextRenderer) {
      return
    }

    beginTextEditing(element)
  }

  private fun selectElement(element: EditorElement) {
    (selectedElement?.renderer as? SelectableRenderer)?.onSelected(false)
    (element.renderer as? SelectableRenderer)?.onSelected(true)
    editorModel.setSelected(element)
    selectedElement = element

    if (isPaintMode) {
      syncDrawingState()
    }

    imageEditorState.invalidate()
  }

  private fun clearSelection() {
    val element = selectedElement ?: return

    (element.renderer as? SelectableRenderer)?.onSelected(false)
    editorModel.setSelected(null)
    selectedElement = null

    if (isPaintMode) {
      syncDrawingState()
    }

    imageEditorState.invalidate()
  }

  fun enterStickerMode() {
    // Re-opening the picker must not record INSERT_STICKER as the mode to come back to.
    if (mode != Mode.INSERT_STICKER) {
      modeBeforeStickerInsertion = mode
    }

    transitionTo(Mode.INSERT_STICKER)
  }

  fun insertSticker(renderer: Renderer) {
    val element = EditorElement(renderer, EditorModel.Z_STICKERS)
    editorModel.addElementCentered(element, STICKER_SCALE)
    drawSessionDirty = true

    // Selecting first leaves the new sticker holding the touch, so a draw session resumed here does not paint over it.
    selectElement(element)
    returnToRestingMode()
  }

  fun cancelStickerInsertion() {
    if (mode == Mode.INSERT_STICKER) {
      transitionTo(modeBeforeStickerInsertion)
    }
  }

  /** Reveals the trash so the drag can end in a delete. */
  fun onDragStarted(element: EditorElement?) {
    if (mode == Mode.CROP || element == null || element.renderer !is SelectableRenderer) {
      return
    }

    selectElement(element)
    isDraggedElementOverTrash = false
    transitionTo(Mode.DELETE)
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

    if (element == null || isOverTrash) {
      element?.let {
        editorModel.delete(it)
        editorModel.updateUndoRedoAvailabilityState()
        drawSessionDirty = true
      }

      clearSelection()
      returnToRestingMode()
    } else {
      element.animatePartialFadeIn(imageEditorState::invalidate)

      // Moving something is not an edit mode of its own, so the drag hands back whatever the user was in -- but the
      // element they were just dragging stays selected.
      returnToRestingMode()
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
    DELETE,
    INSERT_STICKER;

    /** Whether this is something done within a session rather than a session in its own right. */
    internal val isTransient: Boolean
      get() = this == TEXT || this == DELETE || this == INSERT_STICKER
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

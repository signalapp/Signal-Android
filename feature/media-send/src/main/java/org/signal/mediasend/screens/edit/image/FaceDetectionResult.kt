/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.content.Context
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import androidx.annotation.WorkerThread
import org.signal.imageeditor.core.AndroidFaceDetector
import org.signal.imageeditor.core.RendererContext
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.renderers.UriGlideRenderer

/** Widest render we hand to the detector. Faces are found just as well in a smaller image, and far faster. */
private const val MAX_DETECTION_WIDTH = 1000

/**
 * The faces found in an image, along with the render they were found in.
 */
internal class FaceDetectionResult(
  val faces: List<RectF>,
  val renderSize: Point,
  val cropPosition: Matrix
) {
  private val cropPositionValues = FloatArray(9).also { cropPosition.getValues(it) }

  /**
   * Whether these results still describe [model]. A crop or rotation made since detection moves every face, so the
   * image has to be found again rather than masked from stale bounds.
   */
  fun matches(model: EditorModel): Boolean {
    val currentValues = FloatArray(9).also { model.inverseCropPosition.getValues(it) }
    return cropPositionValues.contentEquals(currentValues)
  }
}

/**
 * Renders [model] and finds the faces in the result.
 *
 * The main image's children are left out of that render, so edits already made -- previous masks included -- neither
 * hide a face from the detector nor get mistaken for one.
 */
@WorkerThread
internal fun detectFaces(
  context: Context,
  model: EditorModel,
  typefaceProvider: RendererContext.TypefaceProvider
): FaceDetectionResult {
  val cropPosition = model.inverseCropPosition
  val mainImage = model.mainImage

  // Nothing to detect in an image that has not finished loading: the render would only capture its placeholder.
  if (mainImage == null || (mainImage.renderer as? UriGlideRenderer)?.bitmap == null) {
    return FaceDetectionResult(emptyList(), Point(0, 0), cropPosition)
  }

  mainImage.flags.setChildrenVisible(false)
  val render = try {
    model.render(context, model.getOutputSizeMaxWidth(MAX_DETECTION_WIDTH), typefaceProvider)
  } finally {
    mainImage.flags.reset()
  }

  return try {
    FaceDetectionResult(
      faces = AndroidFaceDetector().detect(render).map { it.bounds },
      renderSize = Point(render.width, render.height),
      cropPosition = cropPosition
    )
  } finally {
    render.recycle()
  }
}

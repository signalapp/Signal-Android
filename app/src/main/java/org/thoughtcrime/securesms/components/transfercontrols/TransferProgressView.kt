/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import kotlin.math.roundToInt

/**
 * This displays a circular progress around an icon. The icon is either an upload arrow, a download arrow, or a rectangular stop button.
 */
class TransferProgressView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
  companion object {
    const val TAG = "TransferProgressView"
    private const val PROGRESS_ARC_STROKE_WIDTH = 1.5f
    private const val ICON_INSET_PERCENT = 0.2f
  }

  private val progressRect = RectF()
  private val stopIconRect = RectF()
  private val progressPaint = progressPaint()
  private val stopIconPaint = stopIconPaint()
  private val trackPaint = trackPaint()

  private var progressPercent = 0f
  private var currentState = State.UNINITIALIZED

  private val downloadDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow_down_24)
  private val uploadDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up_16)

  var startClickListener: OnClickListener? = null
  var cancelClickListener: OnClickListener? = null

  init {
    val tint = ContextCompat.getColor(context, R.color.signal_colorOnCustom)
    val filter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
    downloadDrawable?.colorFilter = filter
    uploadDrawable?.colorFilter = filter
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    when (currentState) {
      State.IN_PROGRESS_CANCELABLE, State.IN_PROGRESS_NON_CANCELABLE -> drawProgress(canvas, progressPercent)
      State.READY_TO_UPLOAD -> sizeAndDrawDrawable(canvas, uploadDrawable)
      State.READY_TO_DOWNLOAD -> sizeAndDrawDrawable(canvas, downloadDrawable)
      State.UNINITIALIZED -> Unit
    }
  }

  fun setDownloading(progress: Float) {
    currentState = State.IN_PROGRESS_CANCELABLE
    if (cancelClickListener == null) {
      Log.i(TAG, "Illegal click listener attached.")
    } else {
      setOnClickListener(cancelClickListener)
    }
    progressPercent = progress
    invalidate()
  }

  fun setUploading(progress: Float) {
    currentState = State.IN_PROGRESS_NON_CANCELABLE
    setOnClickListener { Log.d(TAG, "Not allowed to click an upload.") }
    progressPercent = progress
    invalidate()
  }

  fun setStopped(isUpload: Boolean) {
    val newState = if (isUpload) State.READY_TO_UPLOAD else State.READY_TO_DOWNLOAD
    currentState = newState
    if (startClickListener == null) {
      Log.i(TAG, "Illegal click listener attached.")
    } else {
      setOnClickListener(startClickListener)
    }
    progressPercent = 0f
    invalidate()
  }

  private fun drawProgress(canvas: Canvas, progressPercent: Float) {
    val miniIcon = height < 32.dp
    val stopIconCornerRadius = if (miniIcon) 1f.dp else 4f.dp
    val iconSize: Float = if (miniIcon) 5.5f.dp else 16f.dp
    stopIconRect.set(0f, 0f, iconSize, iconSize)

    canvas.withTranslation(width / 2 - (iconSize / 2), height / 2 - (iconSize / 2)) {
      drawRoundRect(stopIconRect, stopIconCornerRadius, stopIconCornerRadius, stopIconPaint)
    }

    val widthDp = PROGRESS_ARC_STROKE_WIDTH.dp
    val inset = 2.dp
    progressRect.top = widthDp + inset
    progressRect.left = widthDp + inset
    progressRect.right = (width - widthDp) - inset
    progressRect.bottom = (height - widthDp) - inset

    canvas.drawArc(progressRect, 0f, 360f, false, trackPaint)
    canvas.drawArc(progressRect, 270f, 360f * progressPercent, false, progressPaint)
  }

  private fun stopIconPaint(): Paint {
    val stopIconPaint = Paint()
    stopIconPaint.color = ContextCompat.getColor(context, R.color.signal_colorOnCustom)
    stopIconPaint.isAntiAlias = true
    stopIconPaint.style = Paint.Style.FILL
    return stopIconPaint
  }

  private fun trackPaint(): Paint {
    val trackPaint = Paint()
    trackPaint.color = ContextCompat.getColor(context, R.color.signal_colorTransparent2)
    trackPaint.isAntiAlias = true
    trackPaint.style = Paint.Style.STROKE
    trackPaint.strokeWidth = PROGRESS_ARC_STROKE_WIDTH.dp
    return trackPaint
  }

  private fun progressPaint(): Paint {
    val progressPaint = Paint()
    progressPaint.color = ContextCompat.getColor(context, R.color.signal_colorOnCustom)
    progressPaint.isAntiAlias = true
    progressPaint.style = Paint.Style.STROKE
    progressPaint.strokeWidth = PROGRESS_ARC_STROKE_WIDTH.dp
    return progressPaint
  }

  private fun sizeAndDrawDrawable(canvas: Canvas, drawable: Drawable?) {
    if (drawable == null) {
      Log.w(TAG, "Could not load icon for $currentState")
      return
    }

    drawable.setBounds(
      (width * ICON_INSET_PERCENT).roundToInt(),
      (height * ICON_INSET_PERCENT).roundToInt(),
      (width * (1 - ICON_INSET_PERCENT)).roundToInt(),
      (height * (1 - ICON_INSET_PERCENT)).roundToInt()
    )

    drawable.draw(canvas)
  }

  private enum class State {
    IN_PROGRESS_CANCELABLE,
    IN_PROGRESS_NON_CANCELABLE,
    READY_TO_UPLOAD,
    READY_TO_DOWNLOAD,
    UNINITIALIZED
  }
}

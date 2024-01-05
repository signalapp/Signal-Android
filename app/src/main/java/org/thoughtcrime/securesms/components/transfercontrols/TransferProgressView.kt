/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import android.content.Context
import android.content.res.Resources
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
    private const val PROGRESS_ARC_STROKE_WIDTH_DP = 2f
    private const val ICON_SIZE_DP = 24f
    private const val STOP_CORNER_RADIUS_DP = 4f
    private const val PROGRESS_BAR_INSET_DP = 2
  }

  private val iconColor: Int
  private val progressColor: Int
  private val trackColor: Int
  private val stopIconPaint: Paint
  private val progressPaint: Paint
  private val trackPaint: Paint
  private val progressArcStrokeWidth: Float
  private val iconSize: Float
  private val stopIconSize: Float
  private val stopIconCornerRadius: Float

  private val progressRect = RectF()
  private val stopIconRect = RectF()
  private val downloadDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow_down_24)
  private val uploadDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow_up_16)

  private var progressPercent = 0f
  private var currentState = State.UNINITIALIZED

  var startClickListener: OnClickListener? = null
  var cancelClickListener: OnClickListener? = null

  init {
    val displayDensity = Resources.getSystem().displayMetrics.density
    val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.TransferProgressView, 0, 0)
    val signalCustomColor = ContextCompat.getColor(context, R.color.signal_colorOnCustom)
    val signalTransparent2 = ContextCompat.getColor(context, R.color.signal_colorTransparent2)

    iconColor = typedArray.getColor(R.styleable.TransferProgressView_transferIconColor, signalCustomColor)
    progressColor = typedArray.getColor(R.styleable.TransferProgressView_progressColor, signalCustomColor)
    trackColor = typedArray.getColor(R.styleable.TransferProgressView_trackColor, signalTransparent2)
    progressArcStrokeWidth = typedArray.getDimension(R.styleable.TransferProgressView_progressArcWidth, PROGRESS_ARC_STROKE_WIDTH_DP * displayDensity)
    iconSize = typedArray.getDimension(R.styleable.TransferProgressView_iconSize, ICON_SIZE_DP * displayDensity)
    stopIconSize = typedArray.getDimension(R.styleable.TransferProgressView_stopIconSize, ICON_SIZE_DP * displayDensity)
    stopIconCornerRadius = typedArray.getDimension(R.styleable.TransferProgressView_stopIconCornerRadius, STOP_CORNER_RADIUS_DP * displayDensity)

    typedArray.recycle()

    progressPaint = progressPaint(progressColor)
    stopIconPaint = stopIconPaint(iconColor)
    trackPaint = trackPaint(trackColor)

    val filter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
    downloadDrawable?.colorFilter = filter
    uploadDrawable?.colorFilter = filter
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    when (currentState) {
      State.IN_PROGRESS_CANCELABLE -> drawProgress(canvas, progressPercent, true)
      State.IN_PROGRESS_NON_CANCELABLE -> drawProgress(canvas, progressPercent, false)
      State.READY_TO_UPLOAD -> sizeAndDrawDrawable(canvas, uploadDrawable)
      State.READY_TO_DOWNLOAD -> sizeAndDrawDrawable(canvas, downloadDrawable)
      State.UNINITIALIZED -> Unit
    }
  }

  fun setProgress(progress: Float) {
    currentState = State.IN_PROGRESS_CANCELABLE
    if (cancelClickListener == null) {
      Log.i(TAG, "Illegal click listener attached.")
    } else {
      setOnClickListener(cancelClickListener)
    }
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

  private fun drawProgress(canvas: Canvas, progressPercent: Float, showStopIcon: Boolean) {
    if (showStopIcon) {
      stopIconRect.set(0f, 0f, stopIconSize, stopIconSize)

      canvas.withTranslation(width / 2 - (stopIconSize / 2), height / 2 - (stopIconSize / 2)) {
        drawRoundRect(stopIconRect, stopIconCornerRadius, stopIconCornerRadius, stopIconPaint)
      }
    }

    val trackWidthScaled = progressArcStrokeWidth
    val inset: Float = PROGRESS_BAR_INSET_DP * Resources.getSystem().displayMetrics.density
    progressRect.left = trackWidthScaled + inset
    progressRect.top = trackWidthScaled + inset
    progressRect.right = (width - trackWidthScaled) - inset
    progressRect.bottom = (height - trackWidthScaled) - inset

    canvas.drawArc(progressRect, 0f, 360f, false, trackPaint)
    canvas.drawArc(progressRect, 270f, 360f * progressPercent, false, progressPaint)
  }

  private fun stopIconPaint(paintColor: Int): Paint {
    val stopIconPaint = Paint()
    stopIconPaint.color = paintColor
    stopIconPaint.isAntiAlias = true
    stopIconPaint.style = Paint.Style.FILL
    return stopIconPaint
  }

  private fun trackPaint(trackColor: Int): Paint {
    val trackPaint = Paint()
    trackPaint.color = trackColor
    trackPaint.isAntiAlias = true
    trackPaint.style = Paint.Style.STROKE
    trackPaint.strokeWidth = progressArcStrokeWidth
    return trackPaint
  }

  private fun progressPaint(progressColor: Int): Paint {
    val progressPaint = Paint()
    progressPaint.color = progressColor
    progressPaint.isAntiAlias = true
    progressPaint.style = Paint.Style.STROKE
    progressPaint.strokeWidth = progressArcStrokeWidth
    return progressPaint
  }

  private fun sizeAndDrawDrawable(canvas: Canvas, drawable: Drawable?) {
    if (drawable == null) {
      Log.w(TAG, "Could not load icon for $currentState")
      return
    }

    val centerX = width / 2f
    val centerY = height / 2f

    // 0, 0 is the top left corner
    // width, height is the bottom right
    val halfIconSize = (iconSize / 2f)
    val left = (centerX - halfIconSize).roundToInt().coerceAtLeast(0)
    val top = (centerY - halfIconSize).roundToInt().coerceAtLeast(0)
    val right = (centerX + halfIconSize).roundToInt().coerceAtMost(width)
    val bottom = (centerY + halfIconSize).roundToInt().coerceAtMost(height)

    drawable.setBounds(left, top, right, bottom)
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

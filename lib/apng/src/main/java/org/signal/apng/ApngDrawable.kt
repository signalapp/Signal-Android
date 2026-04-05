/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.setBlendMode

class ApngDrawable(val decoder: ApngDecoder) : Drawable(), Animatable {
  companion object {
    private val CLEAR_PAINT = Paint().apply {
      color = Color.TRANSPARENT
      setBlendMode(BlendModeCompat.CLEAR)
    }
    private val DEBUG_PAINT = Paint().apply {
      color = Color.RED
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
  }

  val currentFrame: ApngDecoder.Frame
    get() = decoder.frames[position]
  var position = 0
    private set
  val frameCount: Int
    get() = decoder.frames.size

  var debugDrawBounds = false
  var loopForever = false

  private var playCount = 0

  private val frameRect = Rect(0, 0, 0, 0)

  private var timeForNextFrame = 0L

  private val activeBitmap = Bitmap.createBitmap(decoder.metadata.width, decoder.metadata.height, Bitmap.Config.ARGB_8888)
  private val pendingBitmap = Bitmap.createBitmap(decoder.metadata.width, decoder.metadata.height, Bitmap.Config.ARGB_8888)
  private val disposeOpBitmap = Bitmap.createBitmap(decoder.metadata.width, decoder.metadata.height, Bitmap.Config.ARGB_8888)

  private val pendingCanvas = Canvas(pendingBitmap)
  private val activeCanvas = Canvas(activeBitmap)
  private val disposeOpCanvas = Canvas(disposeOpBitmap)

  private var playing = true

  override fun draw(canvas: Canvas) {
    if (!playing) {
      canvas.drawBitmap(activeBitmap, 0f, 0f, null)
      return
    }

    if (SystemClock.uptimeMillis() < timeForNextFrame) {
      canvas.drawBitmap(activeBitmap, 0f, 0f, null)
      scheduleSelf({ invalidateSelf() }, timeForNextFrame)
      return
    }

    val totalPlays = decoder.metadata.numPlays
    if (playCount >= totalPlays && !loopForever) {
      canvas.drawBitmap(activeBitmap, 0f, 0f, null)
      return
    }

    val frame = decoder.frames[position]
    drawFrame(frame, position)
    canvas.drawBitmap(activeBitmap, 0f, 0f, null)

    position = (position + 1) % decoder.frames.size
    if (position == 0) {
      playCount++
    }

    timeForNextFrame = SystemClock.uptimeMillis() + frame.delayMs
    scheduleSelf({ invalidateSelf() }, timeForNextFrame)
  }

  override fun getIntrinsicWidth(): Int {
    return decoder.metadata.width
  }

  override fun getIntrinsicHeight(): Int {
    return decoder.metadata.height
  }

  override fun setAlpha(alpha: Int) {
    // Not currently implemented
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
    // Not currently implemented
  }

  override fun getOpacity(): Int {
    return PixelFormat.OPAQUE
  }

  override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
    val changed = super.setVisible(visible, restart)
    if (visible) {
      start()
    } else {
      stop()
    }
    return changed
  }

  override fun start() {
    playing = true
    invalidateSelf()
  }

  override fun stop() {
    playing = false
  }

  override fun isRunning(): Boolean {
    return playing
  }

  fun nextFrame() {
    position = (position + 1) % decoder.frames.size
    if (position == 0) {
      playCount++
    }
    drawFrame(decoder.frames[position], position)
  }

  fun prevFrame() {
    if (position == 0) {
      position = decoder.frames.size - 1
      playCount--
    } else {
      position--
    }
    drawFrame(decoder.frames[position], position)
  }

  fun recycle() {
    decoder.close()
    activeBitmap.recycle()
    pendingBitmap.recycle()
    disposeOpBitmap.recycle()
  }

  private fun drawFrame(frame: ApngDecoder.Frame, frameIndex: Int) {
    frameRect.updateBoundsFrom(frame)

    // If the disposeOp is PREVIOUS, then we need to save the contents of the frame before we draw into it
    if (frame.fcTL.disposeOp == ApngDecoder.Chunk.fcTL.DisposeOp.PREVIOUS) {
      disposeOpCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      disposeOpCanvas.drawBitmap(pendingBitmap, 0f, 0f, null)
    }

    // Start with a clean slate if this is the first frame
    if (frameIndex == 0) {
      pendingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    when (frame.fcTL.blendOp) {
      ApngDecoder.Chunk.fcTL.BlendOp.SOURCE -> {
        // This blendOp means that we want all of our new pixels to completely replace the old ones, including the transparent pixels.
        // Normally drawing bitmaps will composite over the existing content, so to allow our new transparent pixels to overwrite old ones,
        // we clear out the drawing region before drawing the new frame.
        pendingCanvas.drawRect(frameRect, CLEAR_PAINT)
      }
      ApngDecoder.Chunk.fcTL.BlendOp.OVER -> {
        // This blendOp means that we composite the new pixels over the old ones, as if layering two PNG's over top of each other in photoshop.
        // We don't need to do anything special here -- the canvas naturally draws bitmaps like this by default.
      }
    }

    val frameBitmap = decoder.decodeFrame(frameIndex)
    pendingCanvas.drawBitmap(frameBitmap, frame.fcTL.xOffset.toFloat(), frame.fcTL.yOffset.toFloat(), null)
    frameBitmap.recycle()

    // Copy the contents of the pending bitmap into the active bitmap
    activeCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    activeCanvas.drawBitmap(pendingBitmap, 0f, 0f, null)
    if (debugDrawBounds) {
      activeCanvas.drawRect(frameRect, DEBUG_PAINT)
    }

    // disposeOp's are how a frame is supposed to clean itself up after it's rendered.
    when (frame.fcTL.disposeOp) {
      ApngDecoder.Chunk.fcTL.DisposeOp.NONE -> {
        // This disposeOp means we don't have to do anything
      }
      ApngDecoder.Chunk.fcTL.DisposeOp.BACKGROUND -> {
        // This disposeOp means that we want to reset the drawing region of the frame to transparent
        pendingCanvas.drawRect(frameRect, CLEAR_PAINT)
      }
      ApngDecoder.Chunk.fcTL.DisposeOp.PREVIOUS -> {
        // This disposeOp means we want to reset the drawing region of the frame to the content that was there before it was drawn.

        // Per spec, if the first frame has a disposeOp of DISPOSE_OP_PREVIOUS, we treat it as DISPOSE_OP_BACKGROUND
        if (frameIndex == 0) {
          pendingCanvas.drawRect(frameRect, CLEAR_PAINT)
        } else {
          pendingCanvas.drawRect(frameRect, CLEAR_PAINT)
          pendingCanvas.drawBitmap(disposeOpBitmap, frameRect, frameRect, null)
        }
      }
    }
  }

  private val ApngDecoder.Frame.delayMs: Long
    get() {
      val delayNumerator = fcTL.delayNum.toInt()
      val delayDenominator = fcTL.delayDen.toInt().takeIf { it > 0 } ?: 100

      return (delayNumerator * 1000 / delayDenominator).toLong()
    }

  private fun Rect.updateBoundsFrom(frame: ApngDecoder.Frame) {
    left = frame.fcTL.xOffset.toInt()
    right = frame.fcTL.xOffset.toInt() + frame.fcTL.width.toInt()
    top = frame.fcTL.yOffset.toInt()
    bottom = frame.fcTL.yOffset.toInt() + frame.fcTL.height.toInt()
  }
}

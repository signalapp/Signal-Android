/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import org.signal.core.util.logging.Log
import kotlin.math.max

/**
 * A center-cropping [TextureView] that keeps its first [SurfaceTexture] across detach and reparenting, so the player
 * never loses its surface. Call [release] when done.
 */
class RetainedTextureView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : TextureView(context, attrs) {

  var callback: Callback? = null

  private var retained: SurfaceTexture? = null
  private var contentWidth = 0
  private var contentHeight = 0

  init {
    surfaceTextureListener = object : SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (retained == null) {
          retained = surface
          callback?.onSurfaceTextureCreated(surface)
        } else if (surface !== retained) {
          // A draw created a new texture before our restore ran; swap ours back in.
          Log.w(TAG, "Replaced the retained texture, restoring it.")
          post { reclaimRetained() }
        }
      }

      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // The framework clears the texture after this returns, so restore it afterwards.
        post { restoreRetained() }
        return false
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    restoreRetained()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    applyCenterCrop()
  }

  fun setContentSize(width: Int, height: Int) {
    contentWidth = width
    contentHeight = height
    applyCenterCrop()
  }

  fun release() {
    retained?.release()
    retained = null
  }

  private fun restoreRetained() {
    val texture = retained ?: return
    if (surfaceTexture == null) {
      setSurfaceTexture(texture)
    }
  }

  private fun reclaimRetained() {
    val texture = retained ?: return
    val current = surfaceTexture
    if (current != null && current !== texture) {
      setSurfaceTexture(texture)
    }
  }

  private fun applyCenterCrop() {
    if (contentWidth <= 0 || contentHeight <= 0 || width == 0 || height == 0) {
      setTransform(null)
      return
    }

    val scale = max(width / contentWidth.toFloat(), height / contentHeight.toFloat())
    val scaleX = contentWidth * scale / width
    val scaleY = contentHeight * scale / height

    setTransform(Matrix().apply { setScale(scaleX, scaleY, width / 2f, height / 2f) })
  }

  companion object {
    private val TAG = Log.tag(RetainedTextureView::class)
  }

  interface Callback {
    fun onSurfaceTextureCreated(surfaceTexture: SurfaceTexture)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.Px
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.window.layout.WindowMetricsCalculator
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.getWindowBreakpoint
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.dp

private val NEXT_PADDING_SMALL = IntOffset(16, 12)
private val NEXT_PADDING_MEDIUM_LARGE = IntOffset(30, 16)

/**
 * Description of the Camera Viewport, Controls, and Toggle position information.
 */
enum class CameraDisplay(
  private val aspectRatio: Float,
  val roundViewFinderCorners: Boolean,
  private val withTogglePositionInfo: PositionInfo,
  private val withoutTogglePositionInfo: PositionInfo,
  private val nextPadding: IntOffset,
  @get:Dimension(unit = Dimension.DP) private val toggleBottomMargin: Int
) {
  DISPLAY_20_9(
    aspectRatio = 9f / 20f,
    roundViewFinderCorners = true,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 130,
      cameraViewportMarginBottomDp = 106,
      cameraViewportGravity = CameraViewportGravity.BOTTOM
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 130,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_SMALL,
    toggleBottomMargin = 20
  ),
  DISPLAY_19_9(
    aspectRatio = 9f / 19f,
    roundViewFinderCorners = true,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 128,
      cameraViewportMarginBottomDp = 104,
      cameraViewportGravity = CameraViewportGravity.BOTTOM
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 128,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_SMALL,
    toggleBottomMargin = 20
  ),
  DISPLAY_18_9(
    aspectRatio = 9f / 18f,
    roundViewFinderCorners = true,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 120,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 84,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_SMALL,
    toggleBottomMargin = 20
  ),
  DISPLAY_16_9(
    aspectRatio = 9f / 16f,
    roundViewFinderCorners = false,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 120,
      cameraViewportGravity = CameraViewportGravity.BOTTOM
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 84,
      cameraViewportGravity = CameraViewportGravity.BOTTOM
    ),
    nextPadding = NEXT_PADDING_SMALL,
    toggleBottomMargin = 20
  ),
  DISPLAY_6_5(
    aspectRatio = 5f / 6f,
    roundViewFinderCorners = false,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 120,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 84,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_SMALL,
    toggleBottomMargin = 20
  ),
  LARGE_PORTRAIT(
    aspectRatio = 9f / 16f,
    roundViewFinderCorners = true,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 0,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 0,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_MEDIUM_LARGE,
    toggleBottomMargin = 20
  ),
  LARGE_LANDSCAPE(
    aspectRatio = 16f / 9f,
    roundViewFinderCorners = true,
    withTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 0,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    withoutTogglePositionInfo = PositionInfo(
      cameraCaptureMarginBottomDp = 0,
      cameraViewportGravity = CameraViewportGravity.CENTER
    ),
    nextPadding = NEXT_PADDING_MEDIUM_LARGE,
    toggleBottomMargin = 20
  );

  fun getNextPaddingEnd(): Int {
    return nextPadding.y
  }

  fun getNextPaddingBottom(): Int {
    return nextPadding.x
  }

  @JvmOverloads
  @Px
  fun getCameraCaptureMarginBottom(resources: Resources, storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled()): Int {
    val positionInfo = if (storiesEnabled) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraCaptureMarginBottomDp.dp - getCameraButtonSizeOffset(resources)
  }

  @JvmOverloads
  @Px
  fun getCameraViewportMarginBottom(storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled()): Int {
    val positionInfo = if (storiesEnabled) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraViewportMarginBottomDp.dp
  }

  @JvmOverloads
  fun getCameraViewportGravity(storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled()): CameraViewportGravity {
    val positionInfo = if (storiesEnabled) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraViewportGravity
  }

  @Dimension(Dimension.DP)
  fun getToggleBottomMargin(): Int {
    return toggleBottomMargin
  }

  /** Whether the viewfinder/card is oriented landscape (only true for [LARGE_LANDSCAPE]). */
  fun isLandscape(): Boolean = aspectRatio > 1f

  companion object {
    @Px
    @JvmStatic
    private fun getCameraButtonSizeOffset(resources: Resources): Int {
      val cameraCaptureButtonSize = resources.getDimensionPixelSize(R.dimen.camera_capture_button_size)
      val cameraCaptureImageButtonSize = resources.getDimensionPixelSize(R.dimen.camera_capture_image_button_size)

      return (cameraCaptureButtonSize - cameraCaptureImageButtonSize) / 2
    }

    /**
     * Get the camera display type given the current window metrics. Note that this
     * will automatically invert the aspect ratio in the case of a non-portrait orientation,
     * since we fix camera to portrait.
     */
    @JvmStatic
    fun getDisplay(activity: Activity): CameraDisplay {
      val windowMetricsCalculator = WindowMetricsCalculator.getOrCreate()
      val windowMetrics = windowMetricsCalculator.computeCurrentWindowMetrics(activity)
      val width = windowMetrics.bounds.width()
      val height = windowMetrics.bounds.height()
      val breakpoint = activity.resources.getWindowBreakpoint()
      val orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT

      return calculateDisplay(breakpoint, orientation, width, height)
    }

    @Composable
    fun rememberCameraDisplay(isLandscape: Boolean): CameraDisplay {
      val breakpoint = rememberWindowBreakpoint()
      val containerSize = LocalWindowInfo.current.containerSize
      val orientation = if (isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT

      return remember(breakpoint, orientation, containerSize) {
        calculateDisplay(breakpoint, orientation, containerSize.width, containerSize.height)
      }
    }

    private fun calculateDisplay(breakpoint: WindowBreakpoint, orientation: Int, width: Int, height: Int): CameraDisplay {
      return if (breakpoint is WindowBreakpoint.Small && orientation == Configuration.ORIENTATION_PORTRAIT) {
        val winAr = width.toFloat() / height
        val aspectRatio = if (winAr > 1f) 1 / winAr else winAr

        when {
          aspectRatio <= DISPLAY_20_9.aspectRatio -> DISPLAY_20_9
          aspectRatio <= DISPLAY_19_9.aspectRatio -> DISPLAY_19_9
          aspectRatio <= DISPLAY_18_9.aspectRatio -> DISPLAY_18_9
          aspectRatio >= DISPLAY_6_5.aspectRatio -> DISPLAY_6_5
          else -> DISPLAY_16_9
        }
      } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        LARGE_PORTRAIT
      } else {
        LARGE_LANDSCAPE
      }
    }
  }

  enum class CameraViewportGravity {
    CENTER,
    BOTTOM
  }

  data class PositionInfo(
    @Dimension(unit = Dimension.DP) val cameraCaptureMarginBottomDp: Int,
    @Dimension(unit = Dimension.DP) val cameraViewportMarginBottomDp: Int = 0,
    val cameraViewportGravity: CameraViewportGravity
  )
}

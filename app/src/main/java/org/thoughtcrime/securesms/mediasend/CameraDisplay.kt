package org.thoughtcrime.securesms.mediasend

import android.app.Activity
import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.Px
import androidx.window.layout.WindowMetricsCalculator
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stories.Stories

/**
 * Description of the Camera Viewport, Controls, and Toggle position information.
 */
enum class CameraDisplay(
  private val aspectRatio: Float,
  val roundViewFinderCorners: Boolean,
  private val withTogglePositionInfo: PositionInfo,
  private val withoutTogglePositionInfo: PositionInfo,
  @Dimension(unit = Dimension.DP) private val toggleBottomMargin: Int
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
    toggleBottomMargin = 52
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
    toggleBottomMargin = 52
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
    toggleBottomMargin = 54
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
    toggleBottomMargin = 54
  );

  @Px
  fun getCameraCaptureMarginBottom(resources: Resources): Int {
    val positionInfo = if (Stories.isFeatureEnabled()) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraCaptureMarginBottomDp.dp - getCameraButtonSizeOffset(resources)
  }

  @Px
  fun getCameraViewportMarginBottom(): Int {
    val positionInfo = if (Stories.isFeatureEnabled()) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraViewportMarginBottomDp.dp
  }

  fun getCameraViewportGravity(): CameraViewportGravity {
    val positionInfo = if (Stories.isFeatureEnabled()) withTogglePositionInfo else withoutTogglePositionInfo

    return positionInfo.cameraViewportGravity
  }

  @Px
  fun getToggleBottomMargin(): Int {
    return toggleBottomMargin.dp
  }

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
      val winAr = width.toFloat() / height
      val aspectRatio = if (winAr > 1f) 1 / winAr else winAr

      return when {
        aspectRatio <= DISPLAY_20_9.aspectRatio -> DISPLAY_20_9
        aspectRatio <= DISPLAY_19_9.aspectRatio -> DISPLAY_19_9
        aspectRatio <= DISPLAY_18_9.aspectRatio -> DISPLAY_18_9
        else -> DISPLAY_16_9
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

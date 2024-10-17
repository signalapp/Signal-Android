package org.thoughtcrime.securesms.mediasend.camerax

import android.content.Context
import android.os.Build
import androidx.camera.view.CameraController
import org.signal.core.util.asListContains
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.video.VideoUtil

/**
 * Describes device capabilities
 */
sealed class CameraXModePolicy {

  abstract val isVideoSupported: Boolean

  abstract val isQrScanEnabled: Boolean

  abstract fun initialize(cameraController: CameraController)

  open fun initialize(cameraController: CameraController, useCaseFlags: Int) {
    if (isQrScanEnabled) {
      cameraController.setEnabledUseCases(useCaseFlags or CameraController.IMAGE_ANALYSIS)
    } else {
      cameraController.setEnabledUseCases(useCaseFlags)
    }
  }

  open fun setToImage(cameraController: CameraController) = Unit

  open fun setToVideo(cameraController: CameraController) = Unit

  /**
   * The device supports having Image and Video enabled at the same time
   */
  data class Mixed(override val isQrScanEnabled: Boolean) : CameraXModePolicy() {

    override val isVideoSupported: Boolean = true

    override fun initialize(cameraController: CameraController) {
      super.initialize(cameraController, CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
    }
  }

  /**
   * The device supports image and video, but only one mode at a time.
   */
  data class Single(override val isQrScanEnabled: Boolean) : CameraXModePolicy() {

    override val isVideoSupported: Boolean = true

    override fun initialize(cameraController: CameraController) {
      setToImage(cameraController)
    }

    override fun setToImage(cameraController: CameraController) {
      super.initialize(cameraController, CameraController.IMAGE_CAPTURE)
    }

    override fun setToVideo(cameraController: CameraController) {
      super.initialize(cameraController, CameraController.VIDEO_CAPTURE)
    }
  }

  /**
   * The device supports taking images only.
   */
  data class ImageOnly(override val isQrScanEnabled: Boolean) : CameraXModePolicy() {

    override val isVideoSupported: Boolean = false

    override fun initialize(cameraController: CameraController) {
      super.initialize(cameraController, CameraController.IMAGE_CAPTURE)
    }
  }

  companion object {
    @JvmStatic
    fun acquire(context: Context, mediaConstraints: MediaConstraints, isVideoEnabled: Boolean, isQrScanEnabled: Boolean): CameraXModePolicy {
      val isVideoSupported = Build.VERSION.SDK_INT >= 26 &&
        isVideoEnabled &&
        MediaConstraints.isVideoTranscodeAvailable() &&
        VideoUtil.getMaxVideoRecordDurationInSeconds(context, mediaConstraints) > 0

      val isMixedModeSupported = isVideoSupported &&
        Build.VERSION.SDK_INT >= 26 &&
        CameraXUtil.isMixedModeSupported(context) &&
        !RemoteConfig.cameraXMixedModelBlocklist.asListContains(Build.MODEL)

      return when {
        isMixedModeSupported -> Mixed(isQrScanEnabled)
        isVideoSupported -> Single(isQrScanEnabled)
        else -> ImageOnly(isQrScanEnabled)
      }
    }
  }
}

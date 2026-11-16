package org.thoughtcrime.securesms.ringrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log

/**
 * Exposes [captureCameraRangesDescription], which lists the fps ranges of the front and back camera
 * that are suitabe for video calls.
 */
object CameraFpsRanges {

  private val TAG = Log.tag(CameraFpsRanges::class)

  /**
   * Descriptions of capture ranges, one camera per line, e.g. "Front: [7,30] [15,15] [30,30]".
   * Only returns fps ranges for the first non-monochrome camera sensor hardware for each direction
   */
  fun captureCameraRangesDescription(context: Context): String? {
    return forCaptureCameras(context)
      .takeIf { it.isNotEmpty() }
      ?.joinToString("\n") { camera ->
        "${camera.facing}: ${camera.ranges.joinToString(" ") { "[${it.lower},${it.upper}]" }}"
      }
  }

  private fun forCaptureCameras(context: Context): List<CameraRanges> {
    val cameraManager: CameraManager = ContextCompat.getSystemService(context, CameraManager::class.java) ?: return emptyList()

    return try {
      val candidates = cameraManager.cameraIdList.filterNot { id ->
        cameraManager.characteristics(id)
          ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
          ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)
          ?: false
      }

      listOf(
        "Front" to CameraMetadata.LENS_FACING_FRONT,
        "Back" to CameraMetadata.LENS_FACING_BACK
      ).mapNotNull { (label, facing) ->
        candidates
          .firstOrNull { cameraManager.characteristics(it)?.get(CameraCharacteristics.LENS_FACING) == facing }
          ?.let { id ->
            val ranges = cameraManager.characteristics(id)
              ?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
              ?.sortedWith(compareBy({ it.lower }, { it.upper }))
              ?: emptyList()
            CameraRanges(label, id, ranges)
          }
          ?.takeIf { it.ranges.isNotEmpty() }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to read camera fps ranges", e)
      emptyList()
    }
  }

  private fun CameraManager.characteristics(cameraId: String): CameraCharacteristics? {
    return try {
      getCameraCharacteristics(cameraId)
    } catch (e: Exception) {
      Log.w(TAG, "Unable to read characteristics for camera $cameraId", e)
      null
    }
  }

  private data class CameraRanges(val facing: String, val cameraId: String, val ranges: List<Range<Int>>)
}

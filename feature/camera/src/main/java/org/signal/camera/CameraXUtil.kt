/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import org.signal.core.util.MemoryFileDescriptor
import org.signal.core.util.logging.Log

object CameraXUtil {
  private val TAG = Log.tag(CameraXUtil::class.java)

  private const val VIDEO_DEBUG_LABEL = "video-capture"
  private const val VIDEO_SIZE = 10L * 1024 * 1024

  @Throws(MemoryFileDescriptor.MemoryFileException::class)
  fun createVideoFileDescriptor(context: Context): MemoryFileDescriptor {
    return MemoryFileDescriptor.newMemoryFileDescriptor(context, VIDEO_DEBUG_LABEL, VIDEO_SIZE)
  }

  private val CAMERA_HARDWARE_LEVEL_ORDERING = intArrayOf(
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
  )

  @RequiresApi(24)
  private val CAMERA_HARDWARE_LEVEL_ORDERING_24 = intArrayOf(
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3
  )

  @RequiresApi(28)
  private val CAMERA_HARDWARE_LEVEL_ORDERING_28 = intArrayOf(
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3
  )

  fun isMixedModeSupported(context: Context): Boolean {
    return getLowestSupportedHardwareLevel(context) != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
  }

  fun getLowestSupportedHardwareLevel(context: Context): Int {
    @SuppressLint("RestrictedApi")
    val cameraManager = CameraManagerCompat.from(context.applicationContext).unwrap()

    try {
      var supported = maxHardwareLevel()

      for (cameraId in cameraManager.cameraIdList) {
        var hwLevel: Int? = null

        try {
          hwLevel = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        } catch (_: NullPointerException) {
          // redmi device crash, assume lowest
        }

        if (hwLevel == null || hwLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
          return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        }

        supported = smallerHardwareLevel(supported, hwLevel)
      }

      return supported
    } catch (e: CameraAccessException) {
      Log.w(TAG, "Failed to enumerate cameras", e)
      return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
    }
  }

  private fun maxHardwareLevel(): Int {
    return if (Build.VERSION.SDK_INT >= 24) {
      CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3
    } else {
      CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
    }
  }

  private fun smallerHardwareLevel(levelA: Int, levelB: Int): Int {
    val hardwareInfoOrdering: IntArray = getHardwareInfoOrdering()
    for (hwInfo in hardwareInfoOrdering) {
      if (levelA == hwInfo || levelB == hwInfo) {
        return hwInfo
      }
    }

    return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
  }

  private fun getHardwareInfoOrdering(): IntArray {
    return when {
      Build.VERSION.SDK_INT >= 28 -> CAMERA_HARDWARE_LEVEL_ORDERING_28
      Build.VERSION.SDK_INT >= 24 -> CAMERA_HARDWARE_LEVEL_ORDERING_24
      else -> CAMERA_HARDWARE_LEVEL_ORDERING
    }
  }
}

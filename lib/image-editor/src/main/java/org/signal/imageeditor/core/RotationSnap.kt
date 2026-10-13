/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.imageeditor.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round

internal data class RotationSnapResult(
  val angleRadians: Double,
  val snapped: Boolean
)

internal object RotationSnap {
  private const val SNAP_ANGLE_RADIANS = PI / 2.0 // 90 degrees in radians
  private val SNAP_THRESHOLD_RADIANS = Math.toRadians(5.0)

  private fun snapToAngle(angleRadians: Double): RotationSnapResult {
    val snappedAngle: Double = round(angleRadians / SNAP_ANGLE_RADIANS) * SNAP_ANGLE_RADIANS

    return if (isCloseEnoughToSnap(angleRadians, snappedAngle)) {
      RotationSnapResult(snappedAngle, true)
    } else {
      RotationSnapResult(angleRadians, false)
    }
  }

  private fun isCloseEnoughToSnap(angleRadians: Double, snappedAngle: Double): Boolean {
    return abs(angleRadians - snappedAngle) <= SNAP_THRESHOLD_RADIANS
  }

  @JvmStatic
  fun snapToAngle(baseAngleRadians: Double, relativeAngleRadians: Double): RotationSnapResult {
    val absoluteAngle = baseAngleRadians + relativeAngleRadians
    val snappedAbsoluteAngle = snapToAngle(absoluteAngle)
    return RotationSnapResult(snappedAbsoluteAngle.angleRadians - baseAngleRadians, snappedAbsoluteAngle.snapped)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.imageeditor.core;

final class RotationSnap {

  private static final double SNAP_ANGLE_RADIANS     = Math.PI / 2d; // 90 degrees in radians
  private static final double SNAP_THRESHOLD_RADIANS = Math.toRadians(5d);

  private RotationSnap() {}

  private static double snapToAngle(double angleRadians) {
    double snappedAngle = Math.rint(angleRadians / SNAP_ANGLE_RADIANS) * SNAP_ANGLE_RADIANS;

    if (Math.abs(angleRadians - snappedAngle) <= SNAP_THRESHOLD_RADIANS) {
      return snappedAngle;
    }

    return angleRadians;
  }

  static double snapToAngle(double baseAngleRadians, double relativeAngleRadians) {
    double absoluteAngle        = baseAngleRadians + relativeAngleRadians;
    double snappedAbsoluteAngle = snapToAngle(absoluteAngle);
    return snappedAbsoluteAngle - baseAngleRadians;
  }
}

/**
 * Copyright (C) 2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.view.Surface;

/**
 * Utility class for {@link Surface}.
 */
public final class SurfaceUtil {

  private SurfaceUtil() {
  }

  /**
   * Converts {@link Surface}.ROTATION_* to degrees.
   *
   * @param surfaceRotation .
   * @return degrees - or 0 when input is invalid
   */
  public static int getDegreesFromSurfaceRotation(int surfaceRotation) {
    switch (surfaceRotation) {
      case Surface.ROTATION_0:
        return 0;
      case Surface.ROTATION_90:
        return 90;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_270:
        return 270;
      default:
        return 0;
    }
  }
}
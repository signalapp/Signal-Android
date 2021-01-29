package org.thoughtcrime.securesms.components.sensors;

import androidx.annotation.NonNull;

public enum Orientation {
  PORTRAIT_BOTTOM_EDGE(0),
  LANDSCAPE_LEFT_EDGE(90),
  LANDSCAPE_RIGHT_EDGE(270);

  private final int degrees;

  Orientation(int degrees) {
    this.degrees = degrees;
  }

  public int getDegrees() {
    return degrees;
  }

  public static @NonNull Orientation fromDegrees(int degrees) {
    for (Orientation orientation : Orientation.values()) {
      if (orientation.degrees == degrees) {
        return orientation;
      }
    }

    return PORTRAIT_BOTTOM_EDGE;
  }
}

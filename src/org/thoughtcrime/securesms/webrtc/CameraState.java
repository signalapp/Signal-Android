package org.thoughtcrime.securesms.webrtc;

import android.support.annotation.NonNull;

public class CameraState {

  public static final CameraState UNKNOWN = new CameraState(Direction.NONE, 0);

  private final Direction activeDirection;
  private final int       cameraCount;

  public CameraState(@NonNull Direction activeDirection, int cameraCount) {
    this.activeDirection = activeDirection;
    this.cameraCount = cameraCount;
  }

  public int getCameraCount() {
    return cameraCount;
  }

  public Direction getActiveDirection() {
    return activeDirection;
  }

  public boolean isEnabled() {
    return this.activeDirection != Direction.NONE;
  }

  public enum Direction {
    FRONT, BACK, NONE, PENDING
  }
}

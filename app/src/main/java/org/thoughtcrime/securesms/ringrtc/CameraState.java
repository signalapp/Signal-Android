package org.thoughtcrime.securesms.ringrtc;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class CameraState implements Parcelable {

  public static final CameraState UNKNOWN = new CameraState(Direction.NONE, 0);

  private final Direction activeDirection;
  private final int       cameraCount;

  public CameraState(@NonNull Direction activeDirection, int cameraCount) {
    this.activeDirection = activeDirection;
    this.cameraCount = cameraCount;
  }

  private CameraState(Parcel in) {
    this(Direction.valueOf(in.readString()), in.readInt());
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

  @Override
  public String toString() {
    return "count: " + cameraCount + ", activeDirection: " + activeDirection;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(activeDirection.name());
    dest.writeInt(cameraCount);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public enum Direction {
    FRONT, BACK, NONE, PENDING;

    public boolean isUsable() {
      return this == FRONT || this == BACK;
    }

    public Direction switchDirection() {
      switch (this) {
        case FRONT:
          return BACK;
        case BACK:
          return FRONT;
        default:
          return this;
      }
    }
  }

  public static final Creator<CameraState> CREATOR = new Creator<CameraState>() {
    @Override
    public CameraState createFromParcel(Parcel in) {
      return new CameraState(in);
    }

    @Override
    public CameraState[] newArray(int size) {
      return new CameraState[size];
    }
  };
}

package org.thoughtcrime.securesms.lock.v2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SvrPin implements Parcelable {

  public static SvrPin EMPTY = new SvrPin("");

  private final String pin;

  private SvrPin(String pin) {
    this.pin = pin;
  }

  private SvrPin(Parcel in) {
    pin = in.readString();
  }

  @Override
  public @NonNull String toString() {
    return pin;
  }

  public static SvrPin from(@Nullable String pin) {
    if (pin == null) return EMPTY;

    pin = pin.trim();

    if (pin.length() == 0) return EMPTY;

    return new SvrPin(pin);
  }

  public int length() {
    return pin.length();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(pin);
  }

  public static final Creator<SvrPin> CREATOR = new Creator<SvrPin>() {
    @Override
    public SvrPin createFromParcel(Parcel in) {
      return new SvrPin(in);
    }

    @Override
    public SvrPin[] newArray(int size) {
      return new SvrPin[size];
    }
  };
}

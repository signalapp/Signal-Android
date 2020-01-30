package org.thoughtcrime.securesms.lock.v2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class KbsPin implements Parcelable {

  public static KbsPin EMPTY = new KbsPin("");

  private final String pin;

  private KbsPin(String pin) {
    this.pin = pin;
  }

  private KbsPin(Parcel in) {
    pin = in.readString();
  }

  @Override
  public @NonNull String toString() {
    return pin;
  }

  public static KbsPin from(@Nullable String pin) {
    if (pin == null) return EMPTY;

    pin = pin.trim();

    if (pin.length() == 0) return EMPTY;

    return new KbsPin(pin);
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

  public static final Creator<KbsPin> CREATOR = new Creator<KbsPin>() {
    @Override
    public KbsPin createFromParcel(Parcel in) {
      return new KbsPin(in);
    }

    @Override
    public KbsPin[] newArray(int size) {
      return new KbsPin[size];
    }
  };
}

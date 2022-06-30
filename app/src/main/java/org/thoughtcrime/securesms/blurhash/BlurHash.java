package org.thoughtcrime.securesms.blurhash;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A BlurHash is a compact string representation of a blurred image that we can use to show fast
 * image previews.
 */
public class BlurHash implements Parcelable {

  private final String hash;

  private BlurHash(@NonNull String hash) {
    this.hash = hash;
  }

  protected BlurHash(Parcel in) {
    hash = in.readString();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(hash);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static @Nullable BlurHash parseOrNull(@Nullable String hash) {
    if (Base83.isValid(hash)) {
      return new BlurHash(hash);
    }
    return null;
  }

  public @NonNull String getHash() {
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlurHash blurHash = (BlurHash) o;
    return Objects.equals(hash, blurHash.hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hash);
  }

  public static final Creator<BlurHash> CREATOR = new Creator<BlurHash>() {
    @Override
    public BlurHash createFromParcel(Parcel in) {
      return new BlurHash(in);
    }

    @Override
    public BlurHash[] newArray(int size) {
      return new BlurHash[size];
    }
  };
}

package org.thoughtcrime.securesms.database.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A wrapper around the primary key of the distribution list database to provide strong typing.
 */
public final class DistributionListId implements DatabaseId, Parcelable {

  public static final long               MY_STORY_ID = 1L;
  public static final DistributionListId MY_STORY    = DistributionListId.from(MY_STORY_ID);

  private final long id;

  public static @NonNull DistributionListId from(long id) {
    if (id <= 0) {
      throw new IllegalArgumentException("Invalid ID! " + id);
    }
    return new DistributionListId(id);
  }

  public static @Nullable DistributionListId fromNullable(long id) {
    if (id > 0) {
      return new DistributionListId(id);
    } else {
      return null;
    }
  }

  private DistributionListId(long id) {
    this.id = id;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(id);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public @NonNull String serialize() {
    return String.valueOf(id);
  }

  @Override
  public @NonNull String toString() {
    return "DistributionListId::" + id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DistributionListId that = (DistributionListId) o;
    return id == that.id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public static final Creator<DistributionListId> CREATOR = new Creator<DistributionListId>() {
    @Override
    public DistributionListId createFromParcel(Parcel in) {
      return new DistributionListId(in.readLong());
    }

    @Override
    public DistributionListId[] newArray(int size) {
      return new DistributionListId[size];
    }
  };
}

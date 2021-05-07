package org.thoughtcrime.securesms.groups;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ParcelableGroupId implements Parcelable {

  private final GroupId groupId;

  public static Parcelable from(@Nullable GroupId groupId) {
    return new ParcelableGroupId(groupId);
  }

  public static @Nullable GroupId get(@Nullable ParcelableGroupId parcelableGroupId) {
    if (parcelableGroupId == null) {
      return null;
    }
    return parcelableGroupId.groupId;
  }

  ParcelableGroupId(@Nullable GroupId groupId) {
    this.groupId = groupId;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    if (groupId != null) {
      dest.writeString(groupId.toString());
    } else {
      dest.writeString(null);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<ParcelableGroupId> CREATOR = new Creator<ParcelableGroupId>() {
    @Override
    public ParcelableGroupId createFromParcel(Parcel in) {
      return new ParcelableGroupId(GroupId.parseNullableOrThrow(in.readString()));
    }

    @Override
    public ParcelableGroupId[] newArray(int size) {
      return new ParcelableGroupId[size];
    }
  };
}

package org.thoughtcrime.securesms.groups;

import android.os.Parcel;
import android.os.Parcelable;

public final class SelectionLimits implements Parcelable {
  private static final int NO_LIMIT = Integer.MAX_VALUE;

  public static final SelectionLimits NO_LIMITS = new SelectionLimits(NO_LIMIT, NO_LIMIT);

  private final int recommendedLimit;
  private final int hardLimit;

  public SelectionLimits(int recommendedLimit, int hardLimit) {
    this.recommendedLimit = recommendedLimit;
    this.hardLimit        = hardLimit;
  }

  public int getRecommendedLimit() {
    return recommendedLimit;
  }

  public int getHardLimit() {
    return hardLimit;
  }

  public boolean hasRecommendedLimit() {
    return recommendedLimit != NO_LIMIT;
  }

  public boolean hasHardLimit() {
    return hardLimit != NO_LIMIT;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(recommendedLimit);
    dest.writeInt(hardLimit);
  }

  public static final Creator<SelectionLimits> CREATOR = new Creator<SelectionLimits>() {
    @Override
    public SelectionLimits createFromParcel(Parcel in) {
      return new SelectionLimits(in.readInt(), in.readInt());
    }

    @Override
    public SelectionLimits[] newArray(int size) {
      return new SelectionLimits[size];
    }
  };

  public SelectionLimits excludingSelf() {
    return excluding(1);
  }

  public SelectionLimits excluding(int count) {
    return new SelectionLimits(recommendedLimit - count, hardLimit - count);
  }
}

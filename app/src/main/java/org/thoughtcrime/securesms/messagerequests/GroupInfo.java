package org.thoughtcrime.securesms.messagerequests;

import androidx.annotation.NonNull;

public final class GroupInfo {
  public static final GroupInfo ZERO = new GroupInfo(0, 0, "");

  private final int    fullMemberCount;
  private final int    pendingMemberCount;
  private final String description;

  public GroupInfo(int fullMemberCount, int pendingMemberCount, @NonNull String description) {
    this.fullMemberCount    = fullMemberCount;
    this.pendingMemberCount = pendingMemberCount;
    this.description        = description;
  }

  public int getFullMemberCount() {
    return fullMemberCount;
  }

  public int getPendingMemberCount() {
    return pendingMemberCount;
  }

  public @NonNull String getDescription() {
    return description;
  }
}

package org.thoughtcrime.securesms.messagerequests;

import androidx.annotation.NonNull;

final class GroupInfo {
  static final GroupInfo ZERO = new GroupInfo(0, 0, "");

  private final int    fullMemberCount;
  private final int    pendingMemberCount;
  private final String description;

  GroupInfo(int fullMemberCount, int pendingMemberCount, @NonNull String description) {
    this.fullMemberCount    = fullMemberCount;
    this.pendingMemberCount = pendingMemberCount;
    this.description        = description;
  }

  int getFullMemberCount() {
    return fullMemberCount;
  }

  int getPendingMemberCount() {
    return pendingMemberCount;
  }

  public @NonNull String getDescription() {
    return description;
  }
}

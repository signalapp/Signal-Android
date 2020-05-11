package org.thoughtcrime.securesms.messagerequests;

final class GroupMemberCount {
  static final GroupMemberCount ZERO = new GroupMemberCount(0, 0);

  private final int fullMemberCount;
  private final int pendingMemberCount;

  GroupMemberCount(int fullMemberCount, int pendingMemberCount) {
    this.fullMemberCount    = fullMemberCount;
    this.pendingMemberCount = pendingMemberCount;
  }

  int getFullMemberCount() {
    return fullMemberCount;
  }

  int getPendingMemberCount() {
    return pendingMemberCount;
  }
}

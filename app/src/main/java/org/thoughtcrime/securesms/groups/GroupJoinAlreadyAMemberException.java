package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

public final class GroupJoinAlreadyAMemberException extends GroupChangeException {

  private final boolean isPending;
  private final boolean isFullMember;

  GroupJoinAlreadyAMemberException(@NonNull Throwable throwable, boolean isPending, boolean isFullMember) {
    super(throwable);
    this.isPending    = isPending;
    this.isFullMember = isFullMember;
  }

  public boolean isPending() {
    return isPending;
  }

  public boolean isFullMember() {
    return isFullMember;
  }
}

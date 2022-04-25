package org.thoughtcrime.securesms.groups;

public final class GroupNotAMemberException extends GroupChangeException {

  private final boolean likelyPendingMember;

  public GroupNotAMemberException(Throwable throwable) {
    super(throwable);
    this.likelyPendingMember = false;
  }

  public GroupNotAMemberException(GroupNotAMemberException throwable, boolean likelyPendingMember) {
    super(throwable.getCause() != null ? throwable.getCause() : throwable);
    this.likelyPendingMember = likelyPendingMember;
  }

  GroupNotAMemberException() {
    this.likelyPendingMember = false;
  }

  public boolean isLikelyPendingMember() {
    return likelyPendingMember;
  }
}

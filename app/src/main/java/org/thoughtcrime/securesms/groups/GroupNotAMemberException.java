package org.thoughtcrime.securesms.groups;

public final class GroupNotAMemberException extends GroupChangeException {

  public GroupNotAMemberException(Throwable throwable) {
    super(throwable);
  }

  GroupNotAMemberException() {
  }
}

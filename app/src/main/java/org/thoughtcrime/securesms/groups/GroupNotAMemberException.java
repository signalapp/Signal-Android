package org.thoughtcrime.securesms.groups;

public final class GroupNotAMemberException extends Exception {

  public GroupNotAMemberException(Throwable throwable) {
    super(throwable);
  }

  GroupNotAMemberException() {
  }
}

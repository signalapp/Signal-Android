package org.thoughtcrime.securesms.groups;

public final class GroupInsufficientRightsException extends GroupChangeException {

  GroupInsufficientRightsException(Throwable throwable) {
    super(throwable);
  }
}

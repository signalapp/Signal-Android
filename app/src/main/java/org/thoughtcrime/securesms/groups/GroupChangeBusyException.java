package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

public final class GroupChangeBusyException extends Exception {

  public GroupChangeBusyException(@NonNull Throwable throwable) {
    super(throwable);
  }

  public GroupChangeBusyException(@NonNull String message) {
    super(message);
  }
}

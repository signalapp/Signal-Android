package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GroupChangeResult {

    public final static GroupChangeResult SUCCESS = new GroupChangeResult(null);

    private final @Nullable GroupChangeFailureReason failureReason;

    GroupChangeResult(@Nullable GroupChangeFailureReason failureReason) {
      this.failureReason = failureReason;
    }

    public static GroupChangeResult failure(@NonNull GroupChangeFailureReason failureReason) {
      return new GroupChangeResult(failureReason);
    }

    public boolean isSuccess() {
      return failureReason == null;
    }

    public @NonNull GroupChangeFailureReason getFailureReason() {
      if (isSuccess()) {
        throw new UnsupportedOperationException();
      }

      return failureReason;
    }
  }

package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.whispersystems.libsignal.util.guava.Optional;

public class ChainParameters {

  private final String  groupId;
  private final boolean ignoreDuplicates;

  private ChainParameters(@NonNull String groupId, boolean ignoreDuplicates) {
    this.groupId          = groupId;
    this.ignoreDuplicates = ignoreDuplicates;
  }

  public Optional<String> getGroupId() {
    return Optional.fromNullable(groupId);
  }

  public boolean shouldIgnoreDuplicates() {
    return ignoreDuplicates;
  }

  public static class Builder {

    private String groupId;
    private boolean ignoreDuplicates;

    public Builder setGroupId(@Nullable String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder ignoreDuplicates(boolean ignore) {
      this.ignoreDuplicates = ignore;
      return this;
    }

    public ChainParameters build() {
      return new ChainParameters(groupId, ignoreDuplicates);
    }
  }
}

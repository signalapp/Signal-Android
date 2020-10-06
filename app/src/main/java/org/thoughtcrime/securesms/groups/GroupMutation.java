package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

public final class GroupMutation {
  @Nullable private final DecryptedGroup       previousGroupState;
  @Nullable private final DecryptedGroupChange groupChange;
  @NonNull  private final DecryptedGroup       newGroupState;

  public GroupMutation(@Nullable DecryptedGroup previousGroupState, @Nullable DecryptedGroupChange groupChange, @NonNull DecryptedGroup newGroupState) {
    this.previousGroupState = previousGroupState;
    this.groupChange        = groupChange;
    this.newGroupState      = newGroupState;
  }

  public @Nullable DecryptedGroup getPreviousGroupState() {
    return previousGroupState;
  }

  public @Nullable DecryptedGroupChange getGroupChange() {
    return groupChange;
  }

  public @NonNull DecryptedGroup getNewGroupState() {
    return newGroupState;
  }
}

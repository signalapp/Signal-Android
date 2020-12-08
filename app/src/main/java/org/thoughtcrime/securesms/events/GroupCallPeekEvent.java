package org.thoughtcrime.securesms.events;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;

public final class GroupCallPeekEvent {
  private final RecipientId groupRecipientId;
  private final String      eraId;
  private final long        deviceCount;
  private final long        deviceLimit;

  public GroupCallPeekEvent(@NonNull RecipientId groupRecipientId, @Nullable String eraId, @Nullable Long deviceCount, @Nullable Long deviceLimit) {
    this.groupRecipientId = groupRecipientId;
    this.eraId            = eraId;
    this.deviceCount      = deviceCount != null ? deviceCount : 0;
    this.deviceLimit      = deviceLimit != null ? deviceLimit : 0;
  }

  public @NonNull RecipientId getGroupRecipientId() {
    return groupRecipientId;
  }

  public boolean isOngoing() {
    return eraId != null && deviceCount > 0;
  }

  public boolean callHasCapacity() {
    return isOngoing() && deviceCount < deviceLimit;
  }
}

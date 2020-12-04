package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collections;
import java.util.List;

/**
 * Describes a change in membership that results from a GV1->GV2 migration.
 */
public final class GroupMigrationMembershipChange {
  private final List<RecipientId> pending;
  private final List<RecipientId> dropped;

  public GroupMigrationMembershipChange(@NonNull List<RecipientId> pending, @NonNull List<RecipientId> dropped) {
    this.pending = pending;
    this.dropped = dropped;
  }

  public static GroupMigrationMembershipChange empty() {
    return new GroupMigrationMembershipChange(Collections.emptyList(), Collections.emptyList());
  }

  public static @NonNull GroupMigrationMembershipChange deserialize(@Nullable String serialized) {
    if (Util.isEmpty(serialized)) {
      return empty();
    } else {
      String[] parts = serialized.split("\\|");
      if (parts.length == 1) {
        return new GroupMigrationMembershipChange(RecipientId.fromSerializedList(parts[0]), Collections.emptyList());
      } else if (parts.length == 2) {
        return new GroupMigrationMembershipChange(RecipientId.fromSerializedList(parts[0]), RecipientId.fromSerializedList(parts[1]));
      } else {
        return GroupMigrationMembershipChange.empty();
      }
    }
  }

  public @NonNull List<RecipientId> getPending() {
    return pending;
  }

  public @NonNull List<RecipientId> getDropped() {
    return dropped;
  }

  public @NonNull String serialize() {
    return RecipientId.toSerializedList(pending) + "|" + RecipientId.toSerializedList(dropped);
  }

  public boolean isEmpty() {
    return pending.isEmpty() && dropped.isEmpty();
  }
}

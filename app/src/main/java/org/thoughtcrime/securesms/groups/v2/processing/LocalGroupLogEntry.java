package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

import java.util.Objects;

/**
 * Pair of a group state and optionally the corresponding change.
 * <p>
 * Similar to {@link ServerGroupLogEntry} but guaranteed to have a group state.
 * <p>
 * Changes are typically not available for pending members.
 */
final class LocalGroupLogEntry {

  @NonNull  private final DecryptedGroup       group;
  @Nullable private final DecryptedGroupChange change;

  LocalGroupLogEntry(@NonNull DecryptedGroup group, @Nullable DecryptedGroupChange change) {
    if (change != null && group.getRevision() != change.getRevision()) {
      throw new AssertionError();
    }

    this.group  = group;
    this.change = change;
  }

  @NonNull DecryptedGroup getGroup() {
    return group;
  }

  @Nullable DecryptedGroupChange getChange() {
    return change;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LocalGroupLogEntry)) return false;

    LocalGroupLogEntry other = (LocalGroupLogEntry) o;

    return group.equals(other.group) && Objects.equals(change, other.change);
  }

  @Override
  public int hashCode() {
    int result = group.hashCode();
    result = 31 * result + (change != null ? change.hashCode() : 0);
    return result;
  }
}

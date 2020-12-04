package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

/**
 * Pair of a group state and optionally the corresponding change from the server.
 * <p>
 * Either the group or change may be empty.
 * <p>
 * Changes are typically not available for pending members.
 */
final class ServerGroupLogEntry {

  private static final String TAG = Log.tag(ServerGroupLogEntry.class);

  @Nullable private final DecryptedGroup       group;
  @Nullable private final DecryptedGroupChange change;

  ServerGroupLogEntry(@Nullable DecryptedGroup group, @Nullable DecryptedGroupChange change) {
    if (change != null && group != null && group.getRevision() != change.getRevision()) {
      Log.w(TAG, "Ignoring change with revision number not matching group");
      change = null;
    }

    if (change == null && group == null) {
      throw new AssertionError();
    }

    this.group  = group;
    this.change = change;
  }

  @Nullable DecryptedGroup getGroup() {
    return group;
  }

  @Nullable DecryptedGroupChange getChange() {
    return change;
  }

  int getRevision() {
         if (group  != null) return group.getRevision();
    else if (change != null) return change.getRevision();
    else                     throw new AssertionError();
  }
}

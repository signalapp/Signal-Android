package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

/**
 * Pair of a {@link DecryptedGroup} and the {@link DecryptedGroupChange} for that version.
 */
public final class DecryptedGroupHistoryEntry {

  private final DecryptedGroup       group;
  private final DecryptedGroupChange change;

  DecryptedGroupHistoryEntry(DecryptedGroup group, DecryptedGroupChange change) {
    if (group.getVersion() != change.getVersion()) {
      throw new AssertionError();
    }

    this.group  = group;
    this.change = change;
  }

  public DecryptedGroup getGroup() {
    return group;
  }

  public DecryptedGroupChange getChange() {
    return change;
  }
}

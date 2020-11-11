package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.groups.GroupId;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation that is backed by a static set of GV2 IDs.
 */
public final class StaticGroupV2ExistenceChecker implements GroupV2ExistenceChecker {

  private final Set<GroupId.V2> ids;

  public StaticGroupV2ExistenceChecker(@NonNull Collection<GroupId.V2> ids) {
    this.ids = new HashSet<>(ids);
  }

  @Override
  public boolean exists(@NonNull GroupId.V2 groupId) {
    return ids.contains(groupId);
  }
}

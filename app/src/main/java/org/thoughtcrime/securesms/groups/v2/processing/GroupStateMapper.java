package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

final class GroupStateMapper {

  static final int LATEST = Integer.MAX_VALUE;

  private static final Comparator<GroupLogEntry> BY_REVISION = (o1, o2) -> Integer.compare(o1.getGroup().getRevision(), o2.getGroup().getRevision());

  private GroupStateMapper() {
  }

  /**
   * Given an input {@link GlobalGroupState} and a {@param maximumRevisionToApply}, returns a result
   * containing what the new local group state should be, and any remaining revision history to apply.
   * <p>
   * Function is pure.
   * @param maximumRevisionToApply Use {@link #LATEST} to apply the very latest.
   */
  static @NonNull AdvanceGroupStateResult partiallyAdvanceGroupState(@NonNull GlobalGroupState inputState,
                                                                     int maximumRevisionToApply)
  {
    final ArrayList<GroupLogEntry> statesToApplyNow    = new ArrayList<>(inputState.getHistory().size());
    final ArrayList<GroupLogEntry> statesToApplyLater  = new ArrayList<>(inputState.getHistory().size());
    final DecryptedGroup           newLocalState;
    final GlobalGroupState         newGlobalGroupState;

    for (GroupLogEntry entry : inputState.getHistory()) {
      if (inputState.getLocalState() != null &&
          inputState.getLocalState().getRevision() >= entry.getGroup().getRevision())
      {
        continue;
      }

      if (entry.getGroup().getRevision() > maximumRevisionToApply) {
        statesToApplyLater.add(entry);
      } else {
        statesToApplyNow.add(entry);
      }
    }

    Collections.sort(statesToApplyNow,   BY_REVISION);
    Collections.sort(statesToApplyLater, BY_REVISION);

    if (statesToApplyNow.size() > 0) {
      newLocalState = statesToApplyNow.get(statesToApplyNow.size() - 1)
                                      .getGroup();
    } else {
      newLocalState = inputState.getLocalState();
    }

    newGlobalGroupState = new GlobalGroupState(newLocalState, statesToApplyLater);

    return new AdvanceGroupStateResult(statesToApplyNow, newGlobalGroupState);
  }
}

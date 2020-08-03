package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;

final class GroupStateMapper {

  private static final String TAG = Log.tag(GroupStateMapper.class);

  static final int LATEST = Integer.MAX_VALUE;

  private static final Comparator<ServerGroupLogEntry> BY_REVISION = (o1, o2) -> Integer.compare(o1.getRevision(), o2.getRevision());

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
    ArrayList<LocalGroupLogEntry>         appliedChanges     = new ArrayList<>(inputState.getServerHistory().size());
    HashMap<Integer, ServerGroupLogEntry> statesToApplyNow   = new HashMap<>(inputState.getServerHistory().size());
    ArrayList<ServerGroupLogEntry>        statesToApplyLater = new ArrayList<>(inputState.getServerHistory().size());
    DecryptedGroup                        current            = inputState.getLocalState();

    if (inputState.getServerHistory().isEmpty()) {
      return new AdvanceGroupStateResult(Collections.emptyList(), new GlobalGroupState(current, Collections.emptyList()));
    }

    for (ServerGroupLogEntry entry : inputState.getServerHistory()) {
      if (entry.getRevision() > maximumRevisionToApply) {
        statesToApplyLater.add(entry);
      } else {
        statesToApplyNow.put(entry.getRevision(), entry);
      }
    }

    Collections.sort(statesToApplyLater, BY_REVISION);

    final int from = inputState.getEarliestRevisionNumber();
    final int to   = Math.min(inputState.getLatestRevisionNumber(), maximumRevisionToApply);

    for (int revision = from; revision >= 0 && revision <= to; revision++) {
      ServerGroupLogEntry entry = statesToApplyNow.get(revision);
      if (entry == null) {
        Log.w(TAG, "Could not find group log on server V" + revision);
        continue;
      }

      DecryptedGroup       groupAtRevision  = entry.getGroup();
      DecryptedGroupChange changeAtRevision = entry.getChange();

      if (current == null) {
        Log.w(TAG, "No local state, accepting server state for V" + revision);
        current = groupAtRevision;
        if (groupAtRevision != null) {
          appliedChanges.add(new LocalGroupLogEntry(groupAtRevision, changeAtRevision));
        }
        continue;
      }

      if (current.getRevision() + 1 != revision) {
        Log.w(TAG, "Detected gap V" + revision);
      }

      if (changeAtRevision == null) {
        Log.w(TAG, "Reconstructing change for V" + revision);
        changeAtRevision = GroupChangeReconstruct.reconstructGroupChange(current, Objects.requireNonNull(groupAtRevision));
      }

      DecryptedGroup groupWithChangeApplied;
      try {
        groupWithChangeApplied = DecryptedGroupUtil.applyWithoutRevisionCheck(current, changeAtRevision);
      } catch (NotAbleToApplyGroupV2ChangeException e) {
        Log.w(TAG, "Unable to apply V" + revision, e);
        continue;
      }

      if (groupAtRevision == null) {
        Log.w(TAG, "Reconstructing state for V" + revision);
        groupAtRevision = groupWithChangeApplied;
      }

      if (current.getRevision() != groupAtRevision.getRevision()) {
        appliedChanges.add(new LocalGroupLogEntry(groupAtRevision, changeAtRevision));
      } else {
        DecryptedGroupChange sameRevisionDelta = GroupChangeReconstruct.reconstructGroupChange(current, groupAtRevision);
        if (!DecryptedGroupUtil.changeIsEmpty(sameRevisionDelta)) {
          appliedChanges.add(new LocalGroupLogEntry(groupAtRevision, sameRevisionDelta));
          Log.w(TAG, "Inserted repair change for mismatch V" + revision);
        }
      }

      DecryptedGroupChange missingChanges = GroupChangeReconstruct.reconstructGroupChange(groupWithChangeApplied, groupAtRevision);
      if (!DecryptedGroupUtil.changeIsEmpty(missingChanges)) {
        appliedChanges.add(new LocalGroupLogEntry(groupAtRevision, missingChanges));
        Log.w(TAG, "Inserted repair change for gap V" + revision);
      }

      current = groupAtRevision;
    }

    return new AdvanceGroupStateResult(appliedChanges, new GlobalGroupState(current, statesToApplyLater));
  }
}

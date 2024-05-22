package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

final class GroupStatePatcher {

  private static final String TAG = Log.tag(GroupStatePatcher.class);

  static final int LATEST                       = Integer.MAX_VALUE;
  static final int PLACEHOLDER_REVISION         = -1;
  static final int RESTORE_PLACEHOLDER_REVISION = -2;

  private static final Comparator<DecryptedGroupChangeLog> BY_REVISION = (o1, o2) -> Integer.compare(o1.getRevision(), o2.getRevision());

  private GroupStatePatcher() {
  }

  /**
   * Given an input {@link GroupStateDiff} and a {@param maximumRevisionToApply}, returns a result
   * containing what the new local group state should be, and any remaining revision history to apply.
   * <p>
   * Function is pure.
   * @param maximumRevisionToApply Use {@link #LATEST} to apply the very latest.
   */
  static @NonNull AdvanceGroupStateResult applyGroupStateDiff(@NonNull GroupStateDiff inputState,
                                                              int maximumRevisionToApply)
  {
    AdvanceGroupStateResult groupStateResult = processChanges(inputState, maximumRevisionToApply);

    return cleanDuplicatedChanges(groupStateResult, inputState.getPreviousGroupState());
  }

  private static @NonNull AdvanceGroupStateResult processChanges(@NonNull GroupStateDiff inputState,
                                                                 int maximumRevisionToApply)
  {
    HashMap<Integer, DecryptedGroupChangeLog>        statesToApplyNow   = new HashMap<>(inputState.getServerHistory().size());
    ArrayList<DecryptedGroupChangeLog>               statesToApplyLater = new ArrayList<>(inputState.getServerHistory().size());
    DecryptedGroup                                   current            = inputState.getPreviousGroupState();
    StateChain<DecryptedGroup, DecryptedGroupChange> stateChain         = createNewMapper();

    if (inputState.getServerHistory().isEmpty()) {
      return new AdvanceGroupStateResult(current);
    }

    for (DecryptedGroupChangeLog entry : inputState.getServerHistory()) {
      if (entry.getRevision() > maximumRevisionToApply) {
        statesToApplyLater.add(entry);
      } else {
        statesToApplyNow.put(entry.getRevision(), entry);
      }
    }

    Collections.sort(statesToApplyLater, BY_REVISION);

    final int from = Math.max(0, inputState.getEarliestRevisionNumber());
    final int to   = Math.min(inputState.getLatestRevisionNumber(), maximumRevisionToApply);

    if (current != null && current.revision == PLACEHOLDER_REVISION) {
      Log.i(TAG, "Ignoring place holder group state");
    } else {
      stateChain.push(current, null);
    }

    for (int revision = from; revision >= 0 && revision <= to; revision++) {
      DecryptedGroupChangeLog entry = statesToApplyNow.get(revision);
      if (entry == null) {
        Log.w(TAG, "Could not find group log on server V" + revision);
        continue;
      }

      if (stateChain.getLatestState() == null && entry.getGroup() != null && current != null && current.revision == PLACEHOLDER_REVISION) {
        DecryptedGroup previousState = entry.getGroup().newBuilder()
                                                       .title(current.title)
                                                       .avatar(current.avatar)
                                                       .build();

        stateChain.push(previousState, null);
      }

      stateChain.push(entry.getGroup(), entry.getChange());
    }

    List<StateChain.Pair<DecryptedGroup, DecryptedGroupChange>> mapperList     = stateChain.getList();
    List<AppliedGroupChangeLog>                                 appliedChanges = new ArrayList<>(mapperList.size());

    for (StateChain.Pair<DecryptedGroup, DecryptedGroupChange> entry : mapperList) {
      if (current == null || entry.getDelta() != null) {
        appliedChanges.add(new AppliedGroupChangeLog(entry.getState(), entry.getDelta()));
      }
    }

    return new AdvanceGroupStateResult(stateChain.getLatestState(), appliedChanges, statesToApplyLater);
  }

  private static AdvanceGroupStateResult cleanDuplicatedChanges(@NonNull AdvanceGroupStateResult groupStateResult,
                                                                @Nullable DecryptedGroup previousGroupState)
  {
    if (previousGroupState == null) return groupStateResult;

    ArrayList<AppliedGroupChangeLog> appliedChanges = new ArrayList<>(groupStateResult.getProcessedLogEntries().size());

    for (AppliedGroupChangeLog entry : groupStateResult.getProcessedLogEntries()) {
      DecryptedGroupChange change = entry.getChange();

      if (change != null) {
        change = GroupChangeUtil.resolveConflict(previousGroupState, change).build();
      }

      appliedChanges.add(new AppliedGroupChangeLog(entry.getGroup(), change));

      previousGroupState = entry.getGroup();
    }

    return new AdvanceGroupStateResult(groupStateResult.getUpdatedGroupState(), appliedChanges, groupStateResult.getRemainingRemoteGroupChanges());
  }

  private static StateChain<DecryptedGroup, DecryptedGroupChange> createNewMapper() {
    return new StateChain<>(
      (group, change) -> {
        try {
          return DecryptedGroupUtil.applyWithoutRevisionCheck(group, change);
        } catch (NotAbleToApplyGroupV2ChangeException e) {
          Log.w(TAG, "Unable to apply V" + change.revision, e);
          return null;
        }
      },
      (groupB, groupA) -> GroupChangeReconstruct.reconstructGroupChange(groupA, groupB),
      (groupA, groupB) -> groupA.revision == groupB.revision && DecryptedGroupUtil.changeIsEmpty(GroupChangeReconstruct.reconstructGroupChange(groupA, groupB))
    );
  }
}

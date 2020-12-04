package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

final class GroupStateMapper {

  private static final String TAG = Log.tag(GroupStateMapper.class);

  static final int LATEST                       = Integer.MAX_VALUE;
  static final int PLACEHOLDER_REVISION         = -1;
  static final int RESTORE_PLACEHOLDER_REVISION = -2;

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
    AdvanceGroupStateResult groupStateResult = processChanges(inputState, maximumRevisionToApply);

    return cleanDuplicatedChanges(groupStateResult, inputState.getLocalState());
  }

  private static @NonNull AdvanceGroupStateResult processChanges(@NonNull GlobalGroupState inputState,
                                                                 int maximumRevisionToApply)
  {
    HashMap<Integer, ServerGroupLogEntry>            statesToApplyNow   = new HashMap<>(inputState.getServerHistory().size());
    ArrayList<ServerGroupLogEntry>                   statesToApplyLater = new ArrayList<>(inputState.getServerHistory().size());
    DecryptedGroup                                   current            = inputState.getLocalState();
    StateChain<DecryptedGroup, DecryptedGroupChange> stateChain         = createNewMapper();

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

    final int from = Math.max(0, inputState.getEarliestRevisionNumber());
    final int to   = Math.min(inputState.getLatestRevisionNumber(), maximumRevisionToApply);

    if (current != null && current.getRevision() == PLACEHOLDER_REVISION) {
      Log.i(TAG, "Ignoring place holder group state");
    } else {
      stateChain.push(current, null);
    }

    for (int revision = from; revision >= 0 && revision <= to; revision++) {
      ServerGroupLogEntry entry = statesToApplyNow.get(revision);
      if (entry == null) {
        Log.w(TAG, "Could not find group log on server V" + revision);
        continue;
      }

      if (stateChain.getLatestState() == null && entry.getGroup() != null && current != null && current.getRevision() == PLACEHOLDER_REVISION) {
        DecryptedGroup previousState = DecryptedGroup.newBuilder(entry.getGroup())
                                                     .setTitle(current.getTitle())
                                                     .setAvatar(current.getAvatar())
                                                     .build();

        stateChain.push(previousState, null);
      }

      stateChain.push(entry.getGroup(), entry.getChange());
    }

    List<StateChain.Pair<DecryptedGroup, DecryptedGroupChange>> mapperList     = stateChain.getList();
    List<LocalGroupLogEntry>                                    appliedChanges = new ArrayList<>(mapperList.size());

    for (StateChain.Pair<DecryptedGroup, DecryptedGroupChange> entry : mapperList) {
      if (current == null || entry.getDelta() != null) {
        appliedChanges.add(new LocalGroupLogEntry(entry.getState(), entry.getDelta()));
      }
    }

    return new AdvanceGroupStateResult(appliedChanges, new GlobalGroupState(stateChain.getLatestState(), statesToApplyLater));
  }

  private static AdvanceGroupStateResult cleanDuplicatedChanges(@NonNull AdvanceGroupStateResult groupStateResult,
                                                                @Nullable DecryptedGroup previousGroupState)
  {
    if (previousGroupState == null) return groupStateResult;

    ArrayList<LocalGroupLogEntry> appliedChanges = new ArrayList<>(groupStateResult.getProcessedLogEntries().size());

    for (LocalGroupLogEntry entry : groupStateResult.getProcessedLogEntries()) {
      DecryptedGroupChange change = entry.getChange();

      if (change != null) {
        change = GroupChangeUtil.resolveConflict(previousGroupState, change).build();
      }

      appliedChanges.add(new LocalGroupLogEntry(entry.getGroup(), change));

      previousGroupState = entry.getGroup();
    }

    return new AdvanceGroupStateResult(appliedChanges, groupStateResult.getNewGlobalGroupState());
  }

  private static StateChain<DecryptedGroup, DecryptedGroupChange> createNewMapper() {
    return new StateChain<>(
      (group, change) -> {
        try {
          return DecryptedGroupUtil.applyWithoutRevisionCheck(group, change);
        } catch (NotAbleToApplyGroupV2ChangeException e) {
          Log.w(TAG, "Unable to apply V" + change.getRevision(), e);
          return null;
        }
      },
      (groupB, groupA) -> GroupChangeReconstruct.reconstructGroupChange(groupA, groupB),
      (groupA, groupB) -> groupA.getRevision() == groupB.getRevision() && DecryptedGroupUtil.changeIsEmpty(GroupChangeReconstruct.reconstructGroupChange(groupA, groupB))
    );
  }
}

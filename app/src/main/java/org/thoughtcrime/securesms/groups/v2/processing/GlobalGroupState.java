package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;

import java.util.Collection;
import java.util.List;

/**
 * Combination of Local and Server group state.
 */
final class GlobalGroupState {

  @Nullable private final DecryptedGroup      localState;
  @NonNull  private final List<GroupLogEntry> history;

  GlobalGroupState(@Nullable DecryptedGroup localState,
                   @NonNull List<GroupLogEntry> serverStates)
  {
    this.localState = localState;
    this.history    = serverStates;
  }

  @Nullable DecryptedGroup getLocalState() {
    return localState;
  }

  @NonNull Collection<GroupLogEntry> getHistory() {
    return history;
  }

  int getLatestVersionNumber() {
    if (history.isEmpty()) {
      if (localState == null) {
        throw new AssertionError();
      }
      return localState.getVersion();
    }
    return history.get(history.size() - 1).getGroup().getVersion();
  }
}

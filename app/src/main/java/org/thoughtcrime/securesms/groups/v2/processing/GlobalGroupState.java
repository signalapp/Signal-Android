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

  @Nullable private final DecryptedGroup            localState;
  @NonNull  private final List<ServerGroupLogEntry> serverHistory;

  GlobalGroupState(@Nullable DecryptedGroup localState,
                   @NonNull List<ServerGroupLogEntry> serverHistory)
  {
    this.localState    = localState;
    this.serverHistory = serverHistory;
  }

  @Nullable DecryptedGroup getLocalState() {
    return localState;
  }

  @NonNull Collection<ServerGroupLogEntry> getServerHistory() {
    return serverHistory;
  }

  int getEarliestRevisionNumber() {
    if (localState != null) {
      return localState.getRevision();
    } else {
      if (serverHistory.isEmpty()) {
        throw new AssertionError();
      }
      return serverHistory.get(0).getRevision();
    }
  }

  int getLatestRevisionNumber() {
    if (serverHistory.isEmpty()) {
      if (localState == null) {
        throw new AssertionError();
      }
      return localState.getRevision();
    }
    return serverHistory.get(serverHistory.size() - 1).getRevision();
  }
}

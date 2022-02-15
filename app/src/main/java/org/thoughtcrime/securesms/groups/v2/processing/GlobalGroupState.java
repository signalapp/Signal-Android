package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage;

import java.util.Collection;
import java.util.List;

/**
 * Combination of Local and Server group state.
 */
final class GlobalGroupState {

  @Nullable private final DecryptedGroup              localState;
  @NonNull  private final List<ServerGroupLogEntry>   serverHistory;
  @NonNull  private final GroupHistoryPage.PagingData pagingData;

  GlobalGroupState(@Nullable DecryptedGroup localState,
                   @NonNull List<ServerGroupLogEntry> serverHistory,
                   @NonNull GroupHistoryPage.PagingData pagingData)
  {
    this.localState    = localState;
    this.serverHistory = serverHistory;
    this.pagingData    = pagingData;
  }

  GlobalGroupState(@Nullable DecryptedGroup localState,
                   @NonNull List<ServerGroupLogEntry> serverHistory)
  {
    this(localState, serverHistory, GroupHistoryPage.PagingData.NONE);
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

  public boolean hasMore() {
    return pagingData.hasMorePages();
  }

  public int getNextPageRevision() {
    if (!pagingData.hasMorePages()) {
      throw new AssertionError("No paging data available");
    }
    return pagingData.getNextPageRevision();
  }
}

package org.whispersystems.signalservice.api.groupsv2;

import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.util.List;

/**
 * Wraps result of group history fetch with it's associated paging data.
 */
public final class GroupHistoryPage {

  private final List<DecryptedGroupHistoryEntry> results;
  private final PagingData                       pagingData;


  public GroupHistoryPage(List<DecryptedGroupHistoryEntry> results, PagingData pagingData) {
    this.results    = results;
    this.pagingData = pagingData;
  }

  public List<DecryptedGroupHistoryEntry> getResults() {
    return results;
  }

  public PagingData getPagingData() {
    return pagingData;
  }

  public static final class PagingData {
    public static final PagingData NONE = new PagingData(false, -1);

    private final boolean hasMorePages;
    private final int     nextPageRevision;

    public static PagingData fromGroup(PushServiceSocket.GroupHistory groupHistory) {
      return new PagingData(groupHistory.hasMore(), groupHistory.hasMore() ? groupHistory.getNextPageStartGroupRevision() : -1);
    }

    private PagingData(boolean hasMorePages, int nextPageRevision) {
      this.hasMorePages     = hasMorePages;
      this.nextPageRevision = nextPageRevision;
    }

    public boolean hasMorePages() {
      return hasMorePages;
    }

    public int getNextPageRevision() {
      return nextPageRevision;
    }
  }
}

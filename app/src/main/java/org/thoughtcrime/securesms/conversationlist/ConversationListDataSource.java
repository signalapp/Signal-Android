package org.thoughtcrime.securesms.conversationlist;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.signal.paging.PagedDataSource;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.conversationlist.model.ConversationReader;
import org.thoughtcrime.securesms.database.MessageTypes;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SignalTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract class ConversationListDataSource implements PagedDataSource<Long, Conversation> {

  private static final String TAG = Log.tag(ConversationListDataSource.class);

  protected final ThreadTable        threadTable;
  protected final ConversationFilter conversationFilter;
  protected final boolean            showConversationFooterTip;

  protected ConversationListDataSource(@NonNull ConversationFilter conversationFilter, boolean showConversationFooterTip) {
    this.threadTable               = SignalDatabase.threads();
    this.conversationFilter        = conversationFilter;
    this.showConversationFooterTip = showConversationFooterTip;
  }

  public static ConversationListDataSource create(@NonNull ConversationFilter conversationFilter, boolean isArchived, boolean showConversationFooterTip) {
    if (!isArchived) return new UnarchivedConversationListDataSource(conversationFilter, showConversationFooterTip);
    else             return new ArchivedConversationListDataSource(conversationFilter, showConversationFooterTip);
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int  count     = getTotalCount();

    if (conversationFilter != ConversationFilter.OFF) {
      count += 1;
    }

    Log.d(TAG, "[size(), " + getClass().getSimpleName() + ", " + conversationFilter + "] " + (System.currentTimeMillis() - startTime) + " ms");
    return Math.max(1, count);
  }

  @Override
  public @NonNull List<Conversation> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    SignalTrace.beginSection("ConversationListDataSource#load");
    Stopwatch stopwatch = new Stopwatch("load(" + start + ", " + length + "), " + getClass().getSimpleName() + ", " + conversationFilter);

    List<Conversation> conversations = new ArrayList<>(length);
    List<Recipient>    recipients    = new LinkedList<>();
    Set<RecipientId>   needsResolve  = new HashSet<>();

    try (ConversationReader reader = new ConversationReader(getCursor(start, length))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        conversations.add(new Conversation(record));
        recipients.add(record.getRecipient());
        needsResolve.add(record.getGroupMessageSender());

        if (!MessageTypes.isGroupV2(record.getType())) {
          needsResolve.add(record.getRecipient().getId());
        } else if (MessageTypes.isGroupUpdate(record.getType())) {
          UpdateDescription description = MessageRecord.getGv2ChangeDescription(ApplicationDependencies.getApplication(), record.getBody(), null);
          needsResolve.addAll(description.getMentioned().stream().map(RecipientId::from).collect(Collectors.toList()));
        }
      }
    }

    stopwatch.split("cursor");

    ApplicationDependencies.getRecipientCache().addToCache(recipients);
    stopwatch.split("cache-recipients");

    Recipient.resolvedList(needsResolve);
    stopwatch.split("recipient-resolve");

    stopwatch.stop(TAG);
    SignalTrace.endSection();

    if (conversations.isEmpty() && start == 0 && length == 1) {
      if (conversationFilter == ConversationFilter.OFF) {
        return Collections.singletonList(new Conversation(ConversationReader.buildThreadRecordForType(Conversation.Type.EMPTY, 0, false)));
      } else {
        return Collections.singletonList(new Conversation(ConversationReader.buildThreadRecordForType(Conversation.Type.CONVERSATION_FILTER_EMPTY,
                                                                                                      0,
                                                                                                      showConversationFooterTip)));
      }
    } else {
      return conversations;
    }
  }

  @Override
  public @Nullable Conversation load(Long threadId) {
    throw new UnsupportedOperationException("Not implemented!");
  }

  @Override
  public @NonNull Long getKey(@NonNull Conversation conversation) {
    return conversation.getThreadRecord().getThreadId();
  }

  protected abstract int getTotalCount();
  protected abstract Cursor getCursor(long offset, long limit);

  private static class ArchivedConversationListDataSource extends ConversationListDataSource {

    private int totalCount;

    ArchivedConversationListDataSource(@NonNull ConversationFilter conversationFilter, boolean showConversationFooterTip) {
      super(conversationFilter, showConversationFooterTip);
    }

    @Override
    protected int getTotalCount() {
      totalCount = threadTable.getArchivedConversationListCount(conversationFilter);
      return totalCount;
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      List<Cursor> cursors = new ArrayList<>(2);
      Cursor       cursor  = threadTable.getArchivedConversationList(conversationFilter, offset, limit);

      cursors.add(cursor);
      if (offset + limit >= totalCount && totalCount > 0 && conversationFilter != ConversationFilter.OFF) {
        MatrixCursor conversationFilterFooter = new MatrixCursor(ConversationReader.FILTER_FOOTER_COLUMNS);
        conversationFilterFooter.addRow(ConversationReader.createConversationFilterFooterRow(showConversationFooterTip));
        cursors.add(conversationFilterFooter);
      }

      return new MergeCursor(cursors.toArray(new Cursor[]{}));
    }
  }

  @VisibleForTesting
  static class UnarchivedConversationListDataSource extends ConversationListDataSource {

    private int totalCount;
    private int pinnedCount;
    private int archivedCount;
    private int unpinnedCount;

    UnarchivedConversationListDataSource(@NonNull ConversationFilter conversationFilter, boolean showConversationFooterTip) {
      super(conversationFilter, showConversationFooterTip);
    }

    @Override
    protected int getTotalCount() {
      int unarchivedCount = threadTable.getUnarchivedConversationListCount(conversationFilter);

      pinnedCount   = threadTable.getPinnedConversationListCount(conversationFilter);
      archivedCount = threadTable.getArchivedConversationListCount(conversationFilter);
      unpinnedCount = unarchivedCount - pinnedCount;
      totalCount    = unarchivedCount;

      if (archivedCount != 0) {
        totalCount++;
      }

      if (pinnedCount != 0) {
        if (unpinnedCount != 0) {
          totalCount += 2;
        } else {
          totalCount += 1;
        }
      }

      return totalCount;
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      List<Cursor> cursors       = new ArrayList<>(5);
      long         originalLimit = limit;

      if (offset == 0 && hasPinnedHeader()) {
        MatrixCursor pinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        pinnedHeaderCursor.addRow(ConversationReader.PINNED_HEADER);
        cursors.add(pinnedHeaderCursor);
        limit--;
      }

      Cursor pinnedCursor = threadTable.getUnarchivedConversationList(conversationFilter, true, offset, limit);
      cursors.add(pinnedCursor);
      limit -= pinnedCursor.getCount();

      if (offset == 0 && hasUnpinnedHeader()) {
        MatrixCursor unpinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        unpinnedHeaderCursor.addRow(ConversationReader.UNPINNED_HEADER);
        cursors.add(unpinnedHeaderCursor);
        limit--;
      }

      long   unpinnedOffset = Math.max(0, offset - pinnedCount - getHeaderOffset());
      Cursor unpinnedCursor = threadTable.getUnarchivedConversationList(conversationFilter, false, unpinnedOffset, limit);
      cursors.add(unpinnedCursor);

      boolean shouldInsertConversationFilterFooter = offset + originalLimit >= totalCount && hasConversationFilterFooter();
      boolean shouldInsertArchivedFooter = offset + originalLimit >= totalCount - (shouldInsertConversationFilterFooter ? 1 : 0) && hasArchivedFooter();
      if (shouldInsertArchivedFooter) {
        MatrixCursor archivedFooterCursor = new MatrixCursor(ConversationReader.ARCHIVED_COLUMNS);
        archivedFooterCursor.addRow(ConversationReader.createArchivedFooterRow(archivedCount));
        cursors.add(archivedFooterCursor);
      }

      if (shouldInsertConversationFilterFooter) {
        MatrixCursor conversationFilterFooter = new MatrixCursor(ConversationReader.FILTER_FOOTER_COLUMNS);
        conversationFilterFooter.addRow(ConversationReader.createConversationFilterFooterRow(showConversationFooterTip));
        cursors.add(conversationFilterFooter);
      }

      return new MergeCursor(cursors.toArray(new Cursor[]{}));
    }

    @VisibleForTesting
    int getHeaderOffset() {
      return (hasPinnedHeader() ? 1 : 0) + (hasUnpinnedHeader() ? 1 : 0);
    }

    @VisibleForTesting
    boolean hasPinnedHeader() {
      return pinnedCount != 0;
    }

    @VisibleForTesting
    boolean hasUnpinnedHeader() {
      return hasPinnedHeader() && unpinnedCount != 0;
    }

    @VisibleForTesting
    boolean hasArchivedFooter() {
      return archivedCount != 0;
    }

    boolean hasConversationFilterFooter() {
      return totalCount >= 1 && conversationFilter != ConversationFilter.OFF;
    }
  }
}

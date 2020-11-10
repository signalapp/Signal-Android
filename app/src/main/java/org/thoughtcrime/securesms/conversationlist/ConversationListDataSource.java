package org.thoughtcrime.securesms.conversationlist;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationReader;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.tracing.Trace;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.paging.Invalidator;
import org.thoughtcrime.securesms.util.paging.SizeFixResult;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

@Trace
abstract class ConversationListDataSource extends PositionalDataSource<Conversation> {

  public static final Executor EXECUTOR = SignalExecutors.newFixedLifoThreadExecutor("signal-conversation-list", 1, 1);

  private static final ThrottledDebouncer THROTTLER = new ThrottledDebouncer(500);

  private static final String TAG = Log.tag(ConversationListDataSource.class);

  protected final ThreadDatabase threadDatabase;

  protected ConversationListDataSource(@NonNull Context context, @NonNull Invalidator invalidator) {
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);

    ContentObserver contentObserver = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        THROTTLER.publish(() -> {
          invalidate();
          context.getContentResolver().unregisterContentObserver(this);
        });
      }
    };

    invalidator.observe(() -> {
      invalidate();
      context.getContentResolver().unregisterContentObserver(contentObserver);
    });

    context.getContentResolver().registerContentObserver(DatabaseContentProviders.ConversationList.CONTENT_URI,  true, contentObserver);
  }

  private static ConversationListDataSource create(@NonNull Context context, @NonNull Invalidator invalidator, boolean isArchived) {
    if (!isArchived) return new UnarchivedConversationListDataSource(context, invalidator);
    else             return new ArchivedConversationListDataSource(context, invalidator);
  }

  @Override
  public final void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<Conversation> callback) {
    long start = System.currentTimeMillis();

    List<Conversation> conversations  = new ArrayList<>(params.requestedLoadSize);
    int                totalCount     = getTotalCount();
    int                effectiveCount = params.requestedStartPosition;
    List<Recipient>    recipients     = new LinkedList<>();

    try (ConversationReader reader = new ConversationReader(getCursor(params.requestedStartPosition, params.requestedLoadSize))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && effectiveCount < totalCount && !isInvalid()) {
        conversations.add(new Conversation(record));
        recipients.add(record.getRecipient());
        effectiveCount++;
      }
    }

    ApplicationDependencies.getRecipientCache().addToCache(recipients);

    if (!isInvalid()) {
      SizeFixResult<Conversation> result = SizeFixResult.ensureMultipleOfPageSize(conversations, params.requestedStartPosition, params.pageSize, totalCount);
      callback.onResult(result.getItems(), params.requestedStartPosition, result.getTotal());
      Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms | start: " + params.requestedStartPosition + ", requestedSize: " + params.requestedLoadSize + ", actualSize: " + result.getItems().size() + ", totalCount: " + result.getTotal() + ", class: " + getClass().getSimpleName());
    } else {
      Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms | start: " + params.requestedStartPosition + ", requestedSize: " + params.requestedLoadSize + ", totalCount: " + totalCount + ", class: " + getClass().getSimpleName() + " -- invalidated");
    }
  }

  @Override
  public final void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<Conversation> callback) {
    long start = System.currentTimeMillis();

    List<Conversation> conversations = new ArrayList<>(params.loadSize);
    List<Recipient>    recipients    = new LinkedList<>();

    try (ConversationReader reader = new ConversationReader(getCursor(params.startPosition, params.loadSize))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        conversations.add(new Conversation(record));
        recipients.add(record.getRecipient());
      }
    }

    ApplicationDependencies.getRecipientCache().addToCache(recipients);

    callback.onResult(conversations);

    Log.d(TAG, "[Update] " + (System.currentTimeMillis() - start) + " ms | start: " + params.startPosition + ", size: " + params.loadSize + ", class: " + getClass().getSimpleName() + (isInvalid() ? " -- invalidated" : ""));
  }

  protected abstract int getTotalCount();
  protected abstract Cursor getCursor(long offset, long limit);

  private static class ArchivedConversationListDataSource extends ConversationListDataSource {

    ArchivedConversationListDataSource(@NonNull Context context, @NonNull Invalidator invalidator) {
      super(context, invalidator);
    }

    @Override
    protected int getTotalCount() {
      return threadDatabase.getArchivedConversationListCount();
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      return threadDatabase.getArchivedConversationList(offset, limit);
    }
  }

  @VisibleForTesting
  static class UnarchivedConversationListDataSource extends ConversationListDataSource {

    private int totalCount;
    private int pinnedCount;
    private int archivedCount;
    private int unpinnedCount;

    UnarchivedConversationListDataSource(@NonNull Context context, @NonNull Invalidator invalidator) {
      super(context, invalidator);
    }

    @Override
    protected int getTotalCount() {
      int unarchivedCount = threadDatabase.getUnarchivedConversationListCount();

      pinnedCount   = threadDatabase.getPinnedConversationListCount();
      archivedCount = threadDatabase.getArchivedConversationListCount();
      unpinnedCount = unarchivedCount - pinnedCount;
      totalCount    = unarchivedCount + (archivedCount != 0 ? 1 : 0) + (pinnedCount != 0 ? (unpinnedCount != 0 ? 2 : 1) : 0);

      return totalCount;
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      List<Cursor> cursors = new ArrayList<>(5);

      if (offset == 0 && hasPinnedHeader()) {
        MatrixCursor pinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        pinnedHeaderCursor.addRow(ConversationReader.PINNED_HEADER);
        cursors.add(pinnedHeaderCursor);
        limit--;
      }

      Cursor pinnedCursor = threadDatabase.getUnarchivedConversationList(true, offset, limit);
      cursors.add(pinnedCursor);
      limit -= pinnedCursor.getCount();

      if (offset == 0 && hasUnpinnedHeader()) {
        MatrixCursor unpinnedHeaderCursor = new MatrixCursor(ConversationReader.HEADER_COLUMN);
        unpinnedHeaderCursor.addRow(ConversationReader.UNPINNED_HEADER);
        cursors.add(unpinnedHeaderCursor);
        limit--;
      }

      long   unpinnedOffset = Math.max(0, offset - pinnedCount - getHeaderOffset());
      Cursor unpinnedCursor = threadDatabase.getUnarchivedConversationList(false, unpinnedOffset, limit);
      cursors.add(unpinnedCursor);

      if (offset + limit >= totalCount && hasArchivedFooter()) {
        MatrixCursor archivedFooterCursor = new MatrixCursor(ConversationReader.ARCHIVED_COLUMNS);
        archivedFooterCursor.addRow(ConversationReader.createArchivedFooterRow(archivedCount));
        cursors.add(archivedFooterCursor);
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
  }

  static class Factory extends DataSource.Factory<Integer, Conversation> {

    private final Context     context;
    private final Invalidator invalidator;
    private final boolean     isArchived;

    public Factory(@NonNull Context context, @NonNull Invalidator invalidator, boolean isArchived) {
      this.context     = context;
      this.invalidator = invalidator;
      this.isArchived  = isArchived;
    }

    @Override
    public @NonNull DataSource<Integer, Conversation> create() {
      return ConversationListDataSource.create(context, invalidator, isArchived);
    }
  }
}

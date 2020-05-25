package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.database.ContentObserver;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource extends PositionalDataSource<MessageRecord> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  public static final Executor EXECUTOR = SignalExecutors.newFixedLifoThreadExecutor("signal-conversation", 1, 1);

  private final Context             context;
  private final long                threadId;
  private final DataUpdatedCallback dataUpdateCallback;

  private ConversationDataSource(@NonNull Context context,
                                 long threadId,
                                 @NonNull Invalidator invalidator,
                                 @NonNull DataUpdatedCallback dataUpdateCallback)
  {
    this.context            = context;
    this.threadId           = threadId;
    this.dataUpdateCallback = dataUpdateCallback;

    ContentObserver contentObserver = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        invalidate();
        context.getContentResolver().unregisterContentObserver(this);
      }
    };

    invalidator.observe(this::invalidate);

    context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadId), true, contentObserver);
  }

  @Override
  public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<MessageRecord> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db             = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records        = new ArrayList<>(params.requestedLoadSize);
    int                 totalCount     = db.getConversationCount(threadId);
    int                 effectiveCount = params.requestedStartPosition;

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.requestedStartPosition, params.requestedLoadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && effectiveCount < totalCount && !isInvalid()) {
        records.add(record);
        effectiveCount++;
      }
    }

    SizeFixResult result = ensureMultipleOfPageSize(records, params.requestedStartPosition, params.pageSize, totalCount);

    callback.onResult(result.messages, params.requestedStartPosition, result.total);
    Util.runOnMain(dataUpdateCallback::onDataUpdated);

    Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms" + (isInvalid() ? " -- invalidated" : ""));
  }

  @Override
  public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<MessageRecord> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db      = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records = new ArrayList<>(params.loadSize);

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.startPosition, params.loadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        records.add(record);
      }
    }

    callback.onResult(records);
    Util.runOnMain(dataUpdateCallback::onDataUpdated);

    Log.d(TAG, "[Update] " + (System.currentTimeMillis() - start) + " ms" + (isInvalid() ? " -- invalidated" : ""));
  }

  private static @NonNull SizeFixResult ensureMultipleOfPageSize(@NonNull List<MessageRecord> records,
                                                                 int startPosition,
                                                                 int pageSize,
                                                                 int total)
  {
    if (records.size() + startPosition == total || records.size() % pageSize == 0) {
      return new SizeFixResult(records, total);
    }

    if (records.size() < pageSize) {
      Log.w(TAG, "Hit a miscalculation where we don't have the full dataset, but it's smaller than a page size. records: " + records.size() + ", startPosition: " + startPosition + ", pageSize: " + pageSize + ", total: " + total);
      return new SizeFixResult(records, records.size() + startPosition);
    }

    Log.w(TAG, "Hit a miscalculation where our data size isn't a multiple of the page size. records: " + records.size() + ", startPosition: " + startPosition + ", pageSize: " + pageSize + ", total: " + total);
    int overflow = records.size() % pageSize;

    return new SizeFixResult(records.subList(0, records.size() - overflow), total);
  }

  private static class SizeFixResult {
    final List<MessageRecord> messages;
    final int                 total;

    private SizeFixResult(@NonNull List<MessageRecord> messages, int total) {
      this.messages = messages;
      this.total    = total;
    }
  }

  interface DataUpdatedCallback {
    void onDataUpdated();
  }

  static class Invalidator {
    private Runnable callback;

    synchronized void invalidate() {
      if (callback != null) {
        callback.run();
      }
    }

    private synchronized void observe(@NonNull Runnable callback) {
      this.callback = callback;
    }
  }

  static class Factory extends DataSource.Factory<Integer, MessageRecord> {

    private final Context             context;
    private final long                threadId;
    private final Invalidator         invalidator;
    private final DataUpdatedCallback callback;

    Factory(Context context, long threadId, @NonNull Invalidator invalidator, @NonNull DataUpdatedCallback callback) {
      this.context     = context;
      this.threadId    = threadId;
      this.invalidator = invalidator;
      this.callback    = callback;
    }

    @Override
    public @NonNull DataSource<Integer, MessageRecord> create() {
      return new ConversationDataSource(context, threadId, invalidator, callback);
    }
  }
}

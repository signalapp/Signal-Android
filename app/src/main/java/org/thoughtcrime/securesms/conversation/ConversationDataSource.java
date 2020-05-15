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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource extends PositionalDataSource<MessageRecord> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  private final Context             context;
  private final long                threadId;
  private final DataUpdatedCallback dataUpdateCallback;

  private ConversationDataSource(@NonNull Context context, long threadId, @NonNull DataUpdatedCallback dataUpdateCallback) {
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

    context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadId), true, contentObserver);
  }

  @Override
  public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<MessageRecord> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db      = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records = new ArrayList<>(params.requestedLoadSize);

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.requestedStartPosition, params.requestedLoadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        records.add(record);
      }
    }

    int effectiveCount = records.size() + params.requestedStartPosition;
    int totalCount     = db.getConversationCount(threadId);

    if (effectiveCount > totalCount) {
      Log.w(TAG, String.format(Locale.ENGLISH, "Miscalculation! Records: %d, Start Position: %d, Total: %d. Adjusting total.",
                                               records.size(),
                                               params.requestedStartPosition,
                                               totalCount));
      totalCount = effectiveCount;
    }

    records = ensureMultipleOfPageSize(records, params.pageSize, totalCount);

    callback.onResult(records, params.requestedStartPosition, totalCount);
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

  private static @NonNull List<MessageRecord> ensureMultipleOfPageSize(@NonNull List<MessageRecord> records, int pageSize, int total) {
    if (records.size() != total && records.size() % pageSize != 0) {
      int overflow = records.size() % pageSize;
      return records.subList(0, records.size() - overflow);
    } else {
      return records;
    }
  }

  interface DataUpdatedCallback {
    void onDataUpdated();
  }

  static class Factory extends DataSource.Factory<Integer, MessageRecord> {

    private final Context             context;
    private final long                threadId;
    private final DataUpdatedCallback callback;

    Factory(Context context, long threadId, @NonNull DataUpdatedCallback callback) {
      this.context  = context;
      this.threadId = threadId;
      this.callback = callback;
    }

    @Override
    public @NonNull DataSource<Integer, MessageRecord> create() {
      return new ConversationDataSource(context, threadId, callback);
    }
  }
}

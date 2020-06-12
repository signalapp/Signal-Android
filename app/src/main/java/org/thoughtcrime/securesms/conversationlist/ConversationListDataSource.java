package org.thoughtcrime.securesms.conversationlist;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.paging.Invalidator;
import org.thoughtcrime.securesms.util.paging.SizeFixResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

abstract class ConversationListDataSource extends PositionalDataSource<Conversation> {

  public static final Executor EXECUTOR = SignalExecutors.newFixedLifoThreadExecutor("signal-conversation-list", 1, 1);

  private static final String TAG = Log.tag(ConversationListDataSource.class);

  protected final ThreadDatabase threadDatabase;

  protected ConversationListDataSource(@NonNull Context context, @NonNull Invalidator invalidator) {
    this.threadDatabase = DatabaseFactory.getThreadDatabase(context);

    ContentObserver contentObserver = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        invalidate();
        context.getContentResolver().unregisterContentObserver(this);
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
    Locale             locale         = Locale.getDefault();
    int                totalCount     = getTotalCount();
    int                effectiveCount = params.requestedStartPosition;

    try (ThreadDatabase.Reader reader = threadDatabase.readerFor(getCursor(params.requestedStartPosition, params.requestedLoadSize))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && effectiveCount < totalCount && !isInvalid()) {
        conversations.add(new Conversation(record, locale));
        effectiveCount++;
      }
    }

    if (!isInvalid()) {
      SizeFixResult<Conversation> result = SizeFixResult.ensureMultipleOfPageSize(conversations, params.requestedStartPosition, params.pageSize, totalCount);

      callback.onResult(result.getItems(), params.requestedStartPosition, result.getTotal());
    }

    Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms" + (isInvalid() ? " -- invalidated" : ""));
  }

  @Override
  public final void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<Conversation> callback) {
    long start = System.currentTimeMillis();

    List<Conversation> conversations = new ArrayList<>(params.loadSize);
    Locale             locale        = Locale.getDefault();

    try (ThreadDatabase.Reader reader = threadDatabase.readerFor(getCursor(params.startPosition, params.loadSize))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        conversations.add(new Conversation(record, locale));
      }
    }

    callback.onResult(conversations);

    Log.d(TAG, "[Update] " + (System.currentTimeMillis() - start) + " ms" + (isInvalid() ? " -- invalidated" : ""));
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

  private static class UnarchivedConversationListDataSource extends ConversationListDataSource {

    UnarchivedConversationListDataSource(@NonNull Context context, @NonNull Invalidator invalidator) {
      super(context, invalidator);
    }

    @Override
    protected int getTotalCount() {
      return threadDatabase.getUnarchivedConversationListCount();
    }

    @Override
    protected Cursor getCursor(long offset, long limit) {
      return threadDatabase.getConversationList(offset, limit);
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

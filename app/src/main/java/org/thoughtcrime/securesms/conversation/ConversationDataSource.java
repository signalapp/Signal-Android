package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.database.ContentObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.tracing.Trace;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.paging.Invalidator;
import org.thoughtcrime.securesms.util.paging.SizeFixResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Core data source for loading an individual conversation.
 */
@Trace
class ConversationDataSource extends PositionalDataSource<ConversationMessage> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  public static final Executor EXECUTOR = SignalExecutors.newFixedLifoThreadExecutor("signal-conversation", 1, 1);

  private final Context             context;
  private final long                threadId;

  private ConversationDataSource(@NonNull Context context,
                                 long threadId,
                                 @NonNull Invalidator invalidator)
  {
    this.context            = context;
    this.threadId           = threadId;

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

    context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadId), true, contentObserver);
  }

  @Override
  public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<ConversationMessage> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db             = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records        = new ArrayList<>(params.requestedLoadSize);
    int                 totalCount     = db.getConversationCount(threadId);
    int                 effectiveCount = params.requestedStartPosition;

    MentionHelper mentionHelper = new MentionHelper();

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.requestedStartPosition, params.requestedLoadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && effectiveCount < totalCount && !isInvalid()) {
        records.add(record);
        mentionHelper.add(record);
        effectiveCount++;
      }
    }

    long mentionStart = System.currentTimeMillis();

    mentionHelper.fetchMentions(context);

    if (!isInvalid()) {
      SizeFixResult<MessageRecord> result = SizeFixResult.ensureMultipleOfPageSize(records, params.requestedStartPosition, params.pageSize, totalCount);

      List<ConversationMessage> items = Stream.of(result.getItems())
                                              .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                                              .toList();

      callback.onResult(items, params.requestedStartPosition, result.getTotal());
      Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms (mentions: " + (System.currentTimeMillis() - mentionStart) + " ms) | thread: " + threadId + ", start: " + params.requestedStartPosition + ", requestedSize: " + params.requestedLoadSize + ", actualSize: " + result.getItems().size() + ", totalCount: " + result.getTotal());
    } else {
      Log.d(TAG, "[Initial Load] " + (System.currentTimeMillis() - start) + " ms | thread: " + threadId + ", start: " + params.requestedStartPosition + ", requestedSize: " + params.requestedLoadSize + ", totalCount: " + totalCount + " -- invalidated");
    }
  }

  @Override
  public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<ConversationMessage> callback) {
    long start = System.currentTimeMillis();

    MmsSmsDatabase      db            = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records       = new ArrayList<>(params.loadSize);
    MentionHelper       mentionHelper = new MentionHelper();

    try (MmsSmsDatabase.Reader reader = db.readerFor(db.getConversation(threadId, params.startPosition, params.loadSize))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !isInvalid()) {
        records.add(record);
        mentionHelper.add(record);
      }
    }

    long mentionStart = System.currentTimeMillis();

    mentionHelper.fetchMentions(context);

    List<ConversationMessage> items = Stream.of(records)
                                            .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                                            .toList();
    callback.onResult(items);

    Log.d(TAG, "[Update] " + (System.currentTimeMillis() - start) + " ms (mentions: " + (System.currentTimeMillis() - mentionStart) + " ms) | thread: " + threadId + ", start: " + params.startPosition + ", size: " + params.loadSize + (isInvalid() ? " -- invalidated" : ""));
  }

  private static class MentionHelper {

    private Collection<Long>         messageIds          = new LinkedList<>();
    private Map<Long, List<Mention>> messageIdToMentions = new HashMap<>();

    void add(MessageRecord record) {
      if (record.isMms()) {
        messageIds.add(record.getId());
      }
    }

    void fetchMentions(Context context) {
      messageIdToMentions = DatabaseFactory.getMentionDatabase(context).getMentionsForMessages(messageIds);
    }

    @Nullable List<Mention> getMentions(long id) {
      return messageIdToMentions.get(id);
    }
  }

  static class Factory extends DataSource.Factory<Integer, ConversationMessage> {

    private final Context             context;
    private final long                threadId;
    private final Invalidator         invalidator;

    Factory(Context context, long threadId, @NonNull Invalidator invalidator) {
      this.context     = context;
      this.threadId    = threadId;
      this.invalidator = invalidator;
    }

    @Override
    public @NonNull DataSource<Integer, ConversationMessage> create() {
      return new ConversationDataSource(context, threadId, invalidator);
    }
  }
}

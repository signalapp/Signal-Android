package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagedDataSource;
import org.thoughtcrime.securesms.conversation.ConversationData.MessageRequestData;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.Stopwatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Core data source for loading an individual conversation.
 */
class ConversationDataSource implements PagedDataSource<ConversationMessage> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  private final Context            context;
  private final long               threadId;
  private final MessageRequestData messageRequestData;

  ConversationDataSource(@NonNull Context context, long threadId, @NonNull MessageRequestData messageRequestData) {
    this.context            = context;
    this.threadId           = threadId;
    this.messageRequestData = messageRequestData;
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int  size      = DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId) + (messageRequestData.includeWarningUpdateMessage() ? 1 : 0);

    Log.d(TAG, "size() for thread " + threadId + ": " + (System.currentTimeMillis() - startTime) + " ms");

    return size;
  }

  @Override
  public @NonNull List<ConversationMessage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    Stopwatch           stopwatch     = new Stopwatch("load(" + start + ", " + length + "), thread " + threadId);
    MmsSmsDatabase      db            = DatabaseFactory.getMmsSmsDatabase(context);
    List<MessageRecord> records       = new ArrayList<>(length);
    MentionHelper       mentionHelper = new MentionHelper();

    try (MmsSmsDatabase.Reader reader = MmsSmsDatabase.readerFor(db.getConversation(threadId, start, length))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        records.add(record);
        mentionHelper.add(record);
      }
    }

    if (messageRequestData.includeWarningUpdateMessage() && (start + length >= size())) {
      records.add(new InMemoryMessageRecord.NoGroupsInCommon(threadId, messageRequestData.isGroup()));
    }

    stopwatch.split("messages");

    mentionHelper.fetchMentions(context);

    stopwatch.split("mentions");

    List<ConversationMessage> messages = Stream.of(records)
                                               .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                                               .toList();

    stopwatch.split("conversion");
    stopwatch.stop(TAG);

    return messages;
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
}

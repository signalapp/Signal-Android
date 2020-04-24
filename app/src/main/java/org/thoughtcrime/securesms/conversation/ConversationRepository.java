package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.Pair;

import java.util.concurrent.Executor;

public class ConversationRepository {

  private final Context  context;
  private final Executor executor;

  public ConversationRepository() {
    this.context  = ApplicationDependencies.getApplication();
    this.executor = SignalExecutors.BOUNDED;
  }

  public void getConversationData(long threadId,
                                  int offset,
                                  int limit,
                                  long lastSeen,
                                  int previousOffset,
                                  boolean firstLoad,
                                  @NonNull Callback<ConversationData> callback)
  {
    executor.execute(() -> callback.onComplete(getConversationDataInternal(threadId, offset, limit, lastSeen, previousOffset, firstLoad)));
  }

  private @NonNull ConversationData getConversationDataInternal(long threadId, int offset, int limit, long lastSeen, int previousOffset, boolean firstLoad) {
    Pair<Long, Boolean> lastSeenAndHasSent = DatabaseFactory.getThreadDatabase(context).getLastSeenAndHasSent(threadId);

    boolean hasSent = lastSeenAndHasSent.second();

    if (lastSeen == -1) {
      lastSeen = lastSeenAndHasSent.first();
    }

    boolean isMessageRequestAccepted     = RecipientUtil.isMessageRequestAccepted(context, threadId);
    boolean hasPreMessageRequestMessages = RecipientUtil.isPreMessageRequestThread(context, threadId);
    Cursor  cursor                       = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId, offset, limit);

    return new ConversationData(cursor, offset, limit, lastSeen, previousOffset, firstLoad, hasSent, isMessageRequestAccepted, hasPreMessageRequestMessages);
  }


  interface Callback<E> {
    void onComplete(@NonNull E result);
  }
}

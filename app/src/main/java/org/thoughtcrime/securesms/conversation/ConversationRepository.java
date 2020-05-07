package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.Pair;

import java.util.concurrent.Executor;

class ConversationRepository {

  private final Context  context;
  private final Executor executor;

  ConversationRepository() {
    this.context  = ApplicationDependencies.getApplication();
    this.executor = SignalExecutors.BOUNDED;
  }

  LiveData<ConversationData> getConversationData(long threadId, long lastSeen, int jumpToPosition) {
    MutableLiveData<ConversationData> liveData = new MutableLiveData<>();

    executor.execute(() -> {
      liveData.postValue(getConversationDataInternal(threadId, lastSeen, jumpToPosition));
    });

    return liveData;
  }

  private @NonNull ConversationData getConversationDataInternal(long threadId, long lastSeen, int jumpToPosition) {
    Pair<Long, Boolean> lastSeenAndHasSent = DatabaseFactory.getThreadDatabase(context).getLastSeenAndHasSent(threadId);

    boolean hasSent = lastSeenAndHasSent.second();

    if (lastSeen == -1) {
      lastSeen = lastSeenAndHasSent.first();
    }

    boolean isMessageRequestAccepted     = RecipientUtil.isMessageRequestAccepted(context, threadId);
    boolean hasPreMessageRequestMessages = RecipientUtil.isPreMessageRequestThread(context, threadId);

    return new ConversationData(lastSeen, hasSent, isMessageRequestAccepted, hasPreMessageRequestMessages, jumpToPosition);
  }
}

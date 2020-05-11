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

  LiveData<ConversationData> getConversationData(long threadId, int jumpToPosition) {
    MutableLiveData<ConversationData> liveData = new MutableLiveData<>();

    executor.execute(() -> {
      liveData.postValue(getConversationDataInternal(threadId, jumpToPosition));
    });

    return liveData;
  }

  private @NonNull ConversationData getConversationDataInternal(long threadId, int jumpToPosition) {
    Pair<Long, Boolean> lastSeenAndHasSent = DatabaseFactory.getThreadDatabase(context).getLastSeenAndHasSent(threadId);

    long    lastSeen         = lastSeenAndHasSent.first();
    boolean hasSent          = lastSeenAndHasSent.second();
    int     lastSeenPosition = 0;

    boolean isMessageRequestAccepted     = RecipientUtil.isMessageRequestAccepted(context, threadId);
    boolean hasPreMessageRequestMessages = RecipientUtil.isPreMessageRequestThread(context, threadId);

    if (lastSeen > 0) {
      lastSeenPosition = DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionForLastSeen(threadId, lastSeen);
    }

    if (lastSeenPosition <= 0) {
      lastSeen = 0;
    }

    return new ConversationData(lastSeen, lastSeenPosition, hasSent, isMessageRequestAccepted, hasPreMessageRequestMessages, jumpToPosition);
  }
}

package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

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
    ThreadDatabase.ConversationMetadata metadata   = DatabaseFactory.getThreadDatabase(context).getConversationMetadata(threadId);
    int                                 threadSize = DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId);

    long    lastSeen             = metadata.getLastSeen();
    boolean hasSent              = metadata.hasSent();
    int     lastSeenPosition     = 0;
    long    lastScrolled         = metadata.getLastScrolled();
    int     lastScrolledPosition = 0;

    boolean isMessageRequestAccepted = RecipientUtil.isMessageRequestAccepted(context, threadId);

    if (lastSeen > 0) {
      lastSeenPosition = DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionOnOrAfterTimestamp(threadId, lastSeen);
    }

    if (lastSeenPosition <= 0) {
      lastSeen = 0;
    }

    if (lastSeen == 0 && lastScrolled > 0) {
      lastScrolledPosition = DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionOnOrAfterTimestamp(threadId, lastScrolled);
    }

    return new ConversationData(threadId, lastSeen, lastSeenPosition, lastScrolledPosition, hasSent, isMessageRequestAccepted, jumpToPosition, threadSize);
  }
}

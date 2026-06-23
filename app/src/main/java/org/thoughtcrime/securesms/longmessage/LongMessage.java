package org.thoughtcrime.securesms.longmessage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.conversation.ConversationMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;

/**
 * A wrapper around a {@link ConversationMessage} and its extra text attachment expanded into a string
 * held in memory.
 */
class LongMessage {

  private final ConversationMessage conversationMessage;
  private final CharSequence        fullBody;

  @WorkerThread
  LongMessage(@NonNull ConversationMessage conversationMessage, @NonNull Context context) {
    this.conversationMessage = conversationMessage;
    this.fullBody            = conversationMessage.getDisplayBody(context);
  }

  @NonNull MessageRecord getMessageRecord() {
    return conversationMessage.getMessageRecord();
  }

  @NonNull CharSequence getFullBody() {
    return fullBody;
  }
}

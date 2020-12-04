package org.thoughtcrime.securesms.longmessage;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.conversation.ConversationMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;

/**
 * A wrapper around a {@link ConversationMessage} and its extra text attachment expanded into a string
 * held in memory.
 */
class LongMessage {

  private final ConversationMessage conversationMessage;
  private final String              fullBody;

  LongMessage(@NonNull ConversationMessage conversationMessage, @NonNull String fullBody) {
    this.conversationMessage = conversationMessage;
    this.fullBody            = fullBody;
  }

  @NonNull MessageRecord getMessageRecord() {
    return conversationMessage.getMessageRecord();
  }

  @NonNull CharSequence getFullBody(@NonNull Context context) {
    return !TextUtils.isEmpty(fullBody) ? fullBody : conversationMessage.getDisplayBody(context);
  }
}

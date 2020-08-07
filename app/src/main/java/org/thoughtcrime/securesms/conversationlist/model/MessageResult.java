package org.thoughtcrime.securesms.conversationlist.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Represents a search result for a message.
 */
public class MessageResult {

  public final Recipient conversationRecipient;
  public final Recipient messageRecipient;
  public final String    body;
  public final String    bodySnippet;
  public final long      threadId;
  public final long      messageId;
  public final long      receivedTimestampMs;
  public final boolean   isMms;

  public MessageResult(@NonNull Recipient conversationRecipient,
                       @NonNull Recipient messageRecipient,
                       @NonNull String body,
                       @NonNull String bodySnippet,
                       long threadId,
                       long messageId,
                       long receivedTimestampMs,
                       boolean isMms)
  {
    this.conversationRecipient = conversationRecipient;
    this.messageRecipient      = messageRecipient;
    this.body                  = body;
    this.bodySnippet           = bodySnippet;
    this.threadId              = threadId;
    this.messageId             = messageId;
    this.receivedTimestampMs   = receivedTimestampMs;
    this.isMms                 = isMms;
  }
}

package org.thoughtcrime.securesms.search.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Represents a search result for a message.
 */
public class MessageResult {

  public final Recipient conversationRecipient;
  public final Recipient messageRecipient;
  public final String    bodySnippet;
  public final long      threadId;
  public final long      receivedTimestampMs;

  public MessageResult(@NonNull Recipient conversationRecipient,
                       @NonNull Recipient messageRecipient,
                       @NonNull String bodySnippet,
                       long threadId,
                       long receivedTimestampMs)
  {
    this.conversationRecipient = conversationRecipient;
    this.messageRecipient      = messageRecipient;
    this.bodySnippet           = bodySnippet;
    this.threadId              = threadId;
    this.receivedTimestampMs   = receivedTimestampMs;
  }
}

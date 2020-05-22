package org.thoughtcrime.securesms.conversation;

/**
 * Represents metadata about a conversation.
 */
final class ConversationData {
  private final long    lastSeen;
  private final int     lastSeenPosition;
  private final boolean hasSent;
  private final boolean isMessageRequestAccepted;
  private final boolean hasPreMessageRequestMessages;
  private final int     jumpToPosition;

  ConversationData(long lastSeen,
                   int lastSeenPosition,
                   boolean hasSent,
                   boolean isMessageRequestAccepted,
                   boolean hasPreMessageRequestMessages,
                   int jumpToPosition)
  {
     this.lastSeen                     = lastSeen;
     this.lastSeenPosition             = lastSeenPosition;
     this.hasSent                      = hasSent;
     this.isMessageRequestAccepted     = isMessageRequestAccepted;
     this.hasPreMessageRequestMessages = hasPreMessageRequestMessages;
     this.jumpToPosition               = jumpToPosition;
  }

  long getLastSeen() {
    return lastSeen;
  }

  int getLastSeenPosition() {
    return lastSeenPosition;
  }

  boolean hasSent() {
    return hasSent;
  }

  boolean isMessageRequestAccepted() {
    return isMessageRequestAccepted;
  }

  boolean hasPreMessageRequestMessages() {
    return hasPreMessageRequestMessages;
  }

  boolean shouldJumpToMessage() {
    return jumpToPosition >= 0;
  }

  int getJumpToPosition() {
    return jumpToPosition;
  }
}

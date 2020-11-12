package org.thoughtcrime.securesms.conversation;

/**
 * Represents metadata about a conversation.
 */
final class ConversationData {
  private final long    threadId;
  private final long    lastSeen;
  private final int     lastSeenPosition;
  private final int     lastScrolledPosition;
  private final boolean hasSent;
  private final boolean isMessageRequestAccepted;
  private final int     jumpToPosition;
  private final int     threadSize;

  ConversationData(long threadId,
                   long lastSeen,
                   int lastSeenPosition,
                   int lastScrolledPosition,
                   boolean hasSent,
                   boolean isMessageRequestAccepted,
                   int jumpToPosition,
                   int threadSize)
  {
    this.threadId                     = threadId;
    this.lastSeen                     = lastSeen;
    this.lastSeenPosition             = lastSeenPosition;
    this.lastScrolledPosition         = lastScrolledPosition;
    this.hasSent                      = hasSent;
    this.isMessageRequestAccepted     = isMessageRequestAccepted;
    this.jumpToPosition               = jumpToPosition;
    this.threadSize                   = threadSize;
  }

  public long getThreadId() {
    return threadId;
  }

  long getLastSeen() {
    return lastSeen;
  }

  int getLastSeenPosition() {
    return lastSeenPosition;
  }

  int getLastScrolledPosition() {
    return lastScrolledPosition;
  }

  boolean hasSent() {
    return hasSent;
  }

  boolean isMessageRequestAccepted() {
    return isMessageRequestAccepted;
  }

  boolean shouldJumpToMessage() {
    return jumpToPosition >= 0;
  }

  boolean shouldScrollToLastSeen() {
    return lastSeenPosition > 0;
  }

  int getJumpToPosition() {
    return jumpToPosition;
  }

  int getThreadSize() {
    return threadSize;
  }
}

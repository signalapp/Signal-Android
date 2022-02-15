package org.thoughtcrime.securesms.conversation;

import androidx.annotation.NonNull;

/**
 * Represents metadata about a conversation.
 */
public final class ConversationData {
  private final long               threadId;
  private final long               lastSeen;
  private final int                lastSeenPosition;
  private final int                lastScrolledPosition;
  private final boolean            hasSent;
  private final int                jumpToPosition;
  private final int                threadSize;
  private final MessageRequestData messageRequestData;
  private final boolean            showUniversalExpireTimerMessage;

  ConversationData(long threadId,
                   long lastSeen,
                   int lastSeenPosition,
                   int lastScrolledPosition,
                   boolean hasSent,
                   int jumpToPosition,
                   int threadSize,
                   @NonNull MessageRequestData messageRequestData,
                   boolean showUniversalExpireTimerMessage)
  {
    this.threadId                        = threadId;
    this.lastSeen                        = lastSeen;
    this.lastSeenPosition                = lastSeenPosition;
    this.lastScrolledPosition            = lastScrolledPosition;
    this.hasSent                         = hasSent;
    this.jumpToPosition                  = jumpToPosition;
    this.threadSize                      = threadSize;
    this.messageRequestData              = messageRequestData;
    this.showUniversalExpireTimerMessage = showUniversalExpireTimerMessage;
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

  @NonNull MessageRequestData getMessageRequestData() {
    return messageRequestData;
  }

  public boolean showUniversalExpireTimerMessage() {
    return showUniversalExpireTimerMessage;
  }

  static final class MessageRequestData {

    private final boolean messageRequestAccepted;
    private final boolean groupsInCommon;
    private final boolean isGroup;

    public MessageRequestData(boolean messageRequestAccepted) {
      this(messageRequestAccepted, false, false);
    }

    public MessageRequestData(boolean messageRequestAccepted, boolean groupsInCommon, boolean isGroup) {
      this.messageRequestAccepted = messageRequestAccepted;
      this.groupsInCommon         = groupsInCommon;
      this.isGroup                = isGroup;
    }

    public boolean isMessageRequestAccepted() {
      return messageRequestAccepted;
    }

    public boolean includeWarningUpdateMessage() {
      return !messageRequestAccepted && !groupsInCommon;
    }

    public boolean isGroup() {
      return isGroup;
    }
  }
}

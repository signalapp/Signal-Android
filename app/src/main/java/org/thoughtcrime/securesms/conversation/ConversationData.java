package org.thoughtcrime.securesms.conversation;

import android.database.Cursor;

import androidx.annotation.NonNull;

public final class ConversationData {
  private final Cursor  cursor;
  private final int     offset;
  private final int     limit;
  private final long    lastSeen;
  private final int     previousOffset;
  private final boolean firstLoad;
  private final boolean hasSent;
  private final boolean isMessageRequestAccepted;
  private final boolean hasPreMessageRequestMessages;

  public ConversationData(Cursor cursor,
                          int offset,
                          int limit,
                          long lastSeen,
                          int previousOffset,
                          boolean firstLoad,
                          boolean hasSent,
                          boolean isMessageRequestAccepted,
                          boolean hasPreMessageRequestMessages)
  {
     this.cursor                       = cursor;
     this.offset                       = offset;
     this.limit                        = limit;
     this.lastSeen                     = lastSeen;
     this.previousOffset               = previousOffset;
     this.firstLoad                    = firstLoad;
     this.hasSent                      = hasSent;
     this.isMessageRequestAccepted     = isMessageRequestAccepted;
     this.hasPreMessageRequestMessages = hasPreMessageRequestMessages;
  }

  public @NonNull Cursor getCursor() {
    return cursor;
  }

  public boolean hasLimit() {
    return limit > 0;
  }

  public int getLimit() {
    return limit;
  }

  public boolean hasOffset() {
        return offset > 0;
    }

  public int getOffset() {
    return offset;
  }

  public int getPreviousOffset() {
    return previousOffset;
  }

  public long getLastSeen() {
        return lastSeen;
    }

  public boolean isFirstLoad() {
    return firstLoad;
  }

  public boolean hasSent() {
    return hasSent;
  }

  public boolean isMessageRequestAccepted() {
    return isMessageRequestAccepted;
  }

  public boolean hasPreMessageRequestMessages() {
    return hasPreMessageRequestMessages;
  }
}

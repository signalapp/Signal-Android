package org.thoughtcrime.securesms.conversationlist.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.ThreadRecord;

public class Conversation {
  private final ThreadRecord threadRecord;
  private final Type         type;

  public Conversation(@NonNull ThreadRecord threadRecord) {
    this.threadRecord = threadRecord;
    if (this.threadRecord.getThreadId() < 0) {
      type = Type.valueOf(this.threadRecord.getBody());
    } else {
      type = Type.THREAD;
    }
  }

  public @NonNull ThreadRecord getThreadRecord() {
    return threadRecord;
  }

  public @NonNull Type getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Conversation that = (Conversation) o;
    return threadRecord.equals(that.threadRecord);
  }

  @Override
  public int hashCode() {
    return threadRecord.hashCode();
  }

  public enum Type {
    THREAD,
    PINNED_HEADER,
    UNPINNED_HEADER,
    ARCHIVED_FOOTER,
    CONVERSATION_FILTER_FOOTER,
    CONVERSATION_FILTER_EMPTY,
    EMPTY
  }
}

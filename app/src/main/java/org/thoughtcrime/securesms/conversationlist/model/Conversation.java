package org.thoughtcrime.securesms.conversationlist.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.ThreadRecord;

public class Conversation {
  private final ThreadRecord threadRecord;

  public Conversation(@NonNull ThreadRecord threadRecord) {
    this.threadRecord = threadRecord;
  }

  public @NonNull ThreadRecord getThreadRecord() {
    return threadRecord;
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
}

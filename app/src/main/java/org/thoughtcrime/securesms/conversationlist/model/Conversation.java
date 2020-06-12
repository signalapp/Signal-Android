package org.thoughtcrime.securesms.conversationlist.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Objects;

public class Conversation {
  private final ThreadRecord threadRecord;
  private final Locale       locale;

  public Conversation(@NonNull ThreadRecord threadRecord, @NonNull Locale locale) {
    this.threadRecord = threadRecord;
    this.locale       = locale;
  }

  public @NonNull ThreadRecord getThreadRecord() {
    return threadRecord;
  }

  public @NonNull Locale getLocale() {
    return locale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Conversation that = (Conversation) o;
    return threadRecord.equals(that.threadRecord) &&
           locale.equals(that.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadRecord, locale);
  }
}

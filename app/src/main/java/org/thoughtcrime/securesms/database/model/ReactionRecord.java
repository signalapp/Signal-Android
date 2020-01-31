package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

public class ReactionRecord {
  private final String      emoji;
  private final RecipientId author;
  private final long        dateSent;
  private final long        dateReceived;

  public ReactionRecord(@NonNull String emoji,
                        @NonNull RecipientId author,
                        long dateSent,
                        long dateReceived)
  {
    this.emoji        = emoji;
    this.author       = author;
    this.dateSent     = dateSent;
    this.dateReceived = dateReceived;
  }

  public @NonNull String getEmoji() {
    return emoji;
  }

  public @NonNull RecipientId getAuthor() {
    return author;
  }

  public long getDateSent() {
    return dateSent;
  }

  public long getDateReceived() {
    return dateReceived;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReactionRecord that = (ReactionRecord) o;
    return dateSent == that.dateSent &&
        dateReceived == that.dateReceived &&
        Objects.equals(emoji, that.emoji) &&
        Objects.equals(author, that.author);
  }

  @Override
  public int hashCode() {
    return Objects.hash(emoji, author, dateSent, dateReceived);
  }
}

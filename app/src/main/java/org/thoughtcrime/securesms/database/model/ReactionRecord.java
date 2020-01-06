package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;

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
}

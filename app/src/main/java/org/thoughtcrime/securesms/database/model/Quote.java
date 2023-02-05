package org.thoughtcrime.securesms.database.model;

import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class Quote {

  private final long            id;
  private final RecipientId     author;
  private final CharSequence    text;
  private final boolean         missing;
  private final SlideDeck       attachment;
  private final List<Mention>   mentions;
  private final QuoteModel.Type quoteType;

  public Quote(long id,
               @NonNull RecipientId author,
               @Nullable CharSequence text,
               boolean missing,
               @NonNull SlideDeck attachment,
               @NonNull List<Mention> mentions,
               @NonNull QuoteModel.Type quoteType)
  {
    this.id         = id;
    this.author     = author;
    this.missing    = missing;
    this.attachment = attachment;
    this.mentions   = mentions;
    this.quoteType  = quoteType;

    SpannableString spannable = SpannableString.valueOf(Util.emptyIfNull(text));
    MentionAnnotation.setMentionAnnotations(spannable, mentions);

    this.text = spannable;
  }

  public @NonNull Quote withAttachment(@NonNull SlideDeck updatedAttachment) {
    return new Quote(id, author, text, missing, updatedAttachment, mentions, quoteType);
  }

  public long getId() {
    return id;
  }

  public @NonNull RecipientId getAuthor() {
    return author;
  }

  public @Nullable CharSequence getDisplayText() {
    return text;
  }

  public boolean isOriginalMissing() {
    return missing;
  }

  public @NonNull SlideDeck getAttachment() {
    return attachment;
  }

  public @NonNull QuoteModel.Type getQuoteType() {
    return quoteType;
  }
}

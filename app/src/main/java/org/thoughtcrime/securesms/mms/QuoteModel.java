package org.thoughtcrime.securesms.mms;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.Collections;
import java.util.List;

public class QuoteModel {

  private final long             id;
  private final RecipientId      author;
  private final String           text;
  private final boolean          missing;
  private final List<Attachment> attachments;
  private final List<Mention>    mentions;
  private final Type             type;
  private final BodyRangeList    bodyRanges;

  public QuoteModel(long id,
                    @NonNull RecipientId author,
                    String text,
                    boolean missing,
                    @Nullable List<Attachment> attachments,
                    @Nullable List<Mention> mentions,
                    @NonNull Type type,
                    @Nullable BodyRangeList bodyRanges)
  {
    this.id          = id;
    this.author      = author;
    this.text        = text;
    this.missing     = missing;
    this.attachments = attachments;
    this.mentions    = mentions != null ? mentions : Collections.emptyList();
    this.type        = type;
    this.bodyRanges  = bodyRanges;
  }

  public long getId() {
    return id;
  }

  public RecipientId getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public boolean isOriginalMissing() {
    return missing;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public @NonNull List<Mention> getMentions() {
    return mentions;
  }

  public Type getType() {
    return type;
  }

  public @Nullable BodyRangeList getBodyRanges() {
    return bodyRanges;
  }

  public enum Type {
    NORMAL(0, SignalServiceDataMessage.Quote.Type.NORMAL),
    GIFT_BADGE(1, SignalServiceDataMessage.Quote.Type.GIFT_BADGE);

    private final int                                 code;
    private final SignalServiceDataMessage.Quote.Type dataMessageType;

    Type(int code, @NonNull SignalServiceDataMessage.Quote.Type dataMessageType) {
      this.code            = code;
      this.dataMessageType = dataMessageType;
    }

    public int getCode() {
      return code;
    }

    public @NonNull SignalServiceDataMessage.Quote.Type getDataMessageType() {
      return dataMessageType;
    }

    public static Type fromCode(int code) {
      for (final Type value : values()) {
        if (value.code == code) {
          return value;
        }
      }

      throw new IllegalArgumentException("Invalid code: " + code);
    }

    public static Type fromDataMessageType(@NonNull SignalServiceDataMessage.Quote.Type dataMessageType) {
      for (final Type value : values()) {
        if (value.dataMessageType == dataMessageType) {
          return value;
        }
      }

      return NORMAL;
    }
  }
}

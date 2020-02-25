package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.Attachment;

public class Sticker {

  private final String     packId;
  private final String     packKey;
  private final int        stickerId;
  private final Attachment attachment;

  public Sticker(@NonNull String packId,
                 @NonNull String packKey,
                 int stickerId,
                 @NonNull Attachment attachment)
  {
    this.packId     = packId;
    this.packKey    = packKey;
    this.stickerId  = stickerId;
    this.attachment = attachment;
  }

  public @NonNull String getPackId() {
    return packId;
  }

  public @NonNull String getPackKey() {
    return packKey;
  }

  public int getStickerId() {
    return stickerId;
  }

  public @NonNull Attachment getAttachment() {
    return attachment;
  }
}

package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;

public class IncomingSticker {

  private final String  packKey;
  private final String  packId;
  private final String  packTitle;
  private final String  packAuthor;
  private final int     stickerId;
  private final String  emoji;
  private final boolean isCover;
  private final boolean isInstalled;

  public IncomingSticker(@NonNull String packId,
                         @NonNull String packKey,
                         @NonNull String packTitle,
                         @NonNull String packAuthor,
                         int stickerId,
                         @NonNull String emoji,
                         boolean isCover,
                         boolean isInstalled)
  {
    this.packId      = packId;
    this.packKey     = packKey;
    this.packTitle   = packTitle;
    this.packAuthor  = packAuthor;
    this.stickerId   = stickerId;
    this.emoji       = emoji;
    this.isCover     = isCover;
    this.isInstalled = isInstalled;
  }

  public @NonNull String getPackKey() {
    return packKey;
  }

  public @NonNull String getPackId() {
    return packId;
  }

  public @NonNull String getPackTitle() {
    return packTitle;
  }

  public @NonNull String getPackAuthor() {
    return packAuthor;
  }

  public int getStickerId() {
    return stickerId;
  }

  public @NonNull String getEmoji() {
    return emoji;
  }

  public boolean isCover() {
    return isCover;
  }

  public boolean isInstalled() {
    return isInstalled;
  }
}

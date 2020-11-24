package org.thoughtcrime.securesms.database.model;

import android.net.Uri;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.mms.PartAuthority;

import java.util.Objects;

/**
 * Represents a record for a sticker pack in the {@link org.thoughtcrime.securesms.database.StickerDatabase}.
 */
public final class StickerRecord {

  private final long    rowId;
  private final String  packId;
  private final String  packKey;
  private final int     stickerId;
  private final String  emoji;
  private final long    size;
  private final boolean isCover;

  public StickerRecord(long rowId,
                       @NonNull String packId,
                       @NonNull String packKey,
                       int stickerId,
                       @NonNull String emoji,
                       long size,
                       boolean isCover)
  {
    this.rowId     = rowId;
    this.packId    = packId;
    this.packKey   = packKey;
    this.stickerId = stickerId;
    this.emoji     = emoji;
    this.size      = size;
    this.isCover   = isCover;
  }

  public long getRowId() {
    return rowId;
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

  public @NonNull Uri getUri() {
    return PartAuthority.getStickerUri(rowId);
  }

  public @NonNull String getEmoji() {
    return emoji;
  }

  public long getSize() {
    return size;
  }

  public boolean isCover() {
    return isCover;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StickerRecord that = (StickerRecord) o;
    return rowId == that.rowId &&
        stickerId == that.stickerId &&
        size == that.size &&
        isCover == that.isCover &&
        packId.equals(that.packId) &&
        packKey.equals(that.packKey) &&
        emoji.equals(that.emoji);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rowId, packId, packKey, stickerId, emoji, size, isCover);
  }
}

package org.thoughtcrime.securesms.database.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.StickerTable;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a record for a sticker pack in the {@link StickerTable}.
 */
public final class StickerPackRecord {

  private final String           packId;
  private final String           packKey;
  private final Optional<String> title;
  private final Optional<String> author;
  private final StickerRecord    cover;
  private final boolean          installed;

  public StickerPackRecord(@NonNull String packId,
                           @NonNull String packKey,
                           @NonNull String title,
                           @NonNull String author,
                           @NonNull StickerRecord cover,
                           boolean installed)
  {
    this.packId    = packId;
    this.packKey   = packKey;
    this.title     = TextUtils.isEmpty(title) ? Optional.empty() : Optional.of(title);
    this.author    = TextUtils.isEmpty(author) ? Optional.empty() : Optional.of(author);
    this.cover     = cover;
    this.installed = installed;
  }

  public @NonNull String getPackId() {
    return packId;
  }

  public @NonNull String getPackKey() {
    return packKey;
  }

  public @NonNull Optional<String> getTitle() {
    return title;
  }

  public @NonNull Optional<String> getAuthor() {
    return author;
  }

  public @NonNull StickerRecord getCover() {
    return cover;
  }

  public boolean isInstalled() {
    return installed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StickerPackRecord record = (StickerPackRecord) o;
    return installed == record.installed &&
        packId.equals(record.packId) &&
        packKey.equals(record.packKey) &&
        title.equals(record.title) &&
        author.equals(record.author) &&
        cover.equals(record.cover);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packId, packKey, title, author, cover, installed);
  }
}

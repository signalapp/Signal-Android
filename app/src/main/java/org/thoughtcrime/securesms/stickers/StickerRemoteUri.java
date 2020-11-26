package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Used as a model to be given to Glide for a sticker that isn't present locally.
 */
public final class StickerRemoteUri implements Key {

  private final String packId;
  private final String packKey;
  private final int    stickerId;

  public StickerRemoteUri(@NonNull String packId, @NonNull String packKey, int stickerId) {
    this.packId    = packId;
    this.packKey   = packKey;
    this.stickerId = stickerId;
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

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(packId.getBytes());
    messageDigest.update(packKey.getBytes());
    messageDigest.update(ByteBuffer.allocate(4).putInt(stickerId).array());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StickerRemoteUri that = (StickerRemoteUri) o;
    return stickerId == that.stickerId &&
        Objects.equals(packId, that.packId) &&
        Objects.equals(packKey, that.packKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packId, packKey, stickerId);
  }
}

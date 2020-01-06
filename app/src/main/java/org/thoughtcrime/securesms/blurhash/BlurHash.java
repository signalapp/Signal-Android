package org.thoughtcrime.securesms.blurhash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A BlurHash is a compact string representation of a blurred image that we can use to show fast
 * image previews.
 */
public class BlurHash {

  private final String hash;

  private BlurHash(@NonNull String hash) {
    this.hash = hash;
  }

  public static @Nullable BlurHash parseOrNull(@Nullable String hash) {
    if (Base83.isValid(hash)) {
      return new BlurHash(hash);
    }
    return null;
  }

  public @NonNull String getHash() {
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlurHash blurHash = (BlurHash) o;
    return Objects.equals(hash, blurHash.hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hash);
  }
}

package org.whispersystems.signalservice.api.storage;

import java.util.Arrays;

/**
 * Key used to encrypt a manifest in the storage service.
 *
 * Created via {@link StorageKey#deriveManifestKey(long)}.
 */
public final class StorageManifestKey implements StorageCipherKey {

  private static final int LENGTH = 32;

  private final byte[] key;

  StorageManifestKey(byte[] key) {
    if (key.length != LENGTH) throw new AssertionError();

    this.key = key;
  }

  @Override
  public byte[] serialize() {
    return key.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;

    return Arrays.equals(((StorageManifestKey) o).key, key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }
}

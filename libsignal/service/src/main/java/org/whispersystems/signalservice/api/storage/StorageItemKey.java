package org.whispersystems.signalservice.api.storage;

import java.util.Arrays;

/**
 * Key used to encrypt individual storage items in the storage service.
 *
 * Created via {@link StorageKey#deriveItemKey(byte[]) }.
 */
public final class StorageItemKey implements StorageCipherKey {

  private static final int LENGTH = 32;

  private final byte[] key;

  StorageItemKey(byte[] key) {
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

    return Arrays.equals(((StorageItemKey) o).key, key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }
}

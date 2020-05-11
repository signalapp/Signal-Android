package org.whispersystems.signalservice.api.storage;

import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.util.Base64;
import org.whispersystems.util.StringUtil;

import java.util.Arrays;

import static org.whispersystems.signalservice.api.crypto.CryptoUtil.hmacSha256;

/**
 * Key used to encrypt data on the storage service. Not used directly -- instead we used keys that
 * are derived for each item we're storing.
 *
 * Created via {@link MasterKey#deriveStorageServiceKey()}.
 */
public final class StorageKey {

  private static final int LENGTH = 32;

  private final byte[] key;

  public StorageKey(byte[] key) {
    if (key.length != LENGTH) throw new AssertionError();

    this.key = key;
  }

  public StorageManifestKey deriveManifestKey(long version) {
    return new StorageManifestKey(derive("Manifest_" + version));
  }

  public StorageItemKey deriveItemKey(byte[] key) {
    return new StorageItemKey(derive("Item_" + Base64.encodeBytes(key)));
  }

  private byte[] derive(String keyName) {
    return hmacSha256(key, StringUtil.utf8(keyName));
  }

  public byte[] serialize() {
    return key.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;

    return Arrays.equals(((StorageKey) o).key, key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }
}

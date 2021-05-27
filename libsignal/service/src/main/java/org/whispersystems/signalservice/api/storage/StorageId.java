package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.util.Arrays;
import java.util.Objects;

public class StorageId {
  private final int    type;
  private final byte[] raw;

  public static StorageId forContact(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.CONTACT_VALUE, Preconditions.checkNotNull(raw));
  }

  public static StorageId forGroupV1(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.GROUPV1_VALUE, Preconditions.checkNotNull(raw));
  }

  public static StorageId forGroupV2(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.GROUPV2_VALUE, Preconditions.checkNotNull(raw));
  }

  public static StorageId forAccount(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.ACCOUNT_VALUE, Preconditions.checkNotNull(raw));
  }

  public static StorageId forType(byte[] raw, int type) {
    return new StorageId(type, raw);
  }

  public boolean isUnknown() {
    return !isKnownType(type);
  }

  private StorageId(int type, byte[] raw) {
    this.type = type;
    this.raw  = raw;
  }

  public int getType() {
    return type;
  }

  public byte[] getRaw() {
    return raw;
  }

  public StorageId withNewBytes(byte[] key) {
    return new StorageId(type, key);
  }

  public static boolean isKnownType(int val) {
    for (ManifestRecord.Identifier.Type type : ManifestRecord.Identifier.Type.values()) {
      if (type != ManifestRecord.Identifier.Type.UNRECOGNIZED && type.getNumber() == val) {
        return true;
      }
    }
    return false;
  }

  public static int largestKnownType() {
    int max = 0;

    for (ManifestRecord.Identifier.Type type : ManifestRecord.Identifier.Type.values()) {
      if (type != ManifestRecord.Identifier.Type.UNRECOGNIZED) {
        max = Math.max(type.getNumber(), max);
      }
    }

    return max;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StorageId storageId = (StorageId) o;
    return type == storageId.type &&
        Arrays.equals(raw, storageId.raw);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type);
    result = 31 * result + Arrays.hashCode(raw);
    return result;
  }
}

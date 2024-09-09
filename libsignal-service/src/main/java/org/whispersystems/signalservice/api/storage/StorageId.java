package org.whispersystems.signalservice.api.storage;


import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.util.Arrays;
import java.util.Objects;

public class StorageId {
  private final int    type;
  private final byte[] raw;

  public static StorageId forContact(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.CONTACT.getValue(), Preconditions.checkNotNull(raw));
  }

  public static StorageId forGroupV1(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.GROUPV1.getValue(), Preconditions.checkNotNull(raw));
  }

  public static StorageId forGroupV2(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.GROUPV2.getValue(), Preconditions.checkNotNull(raw));
  }

  public static StorageId forStoryDistributionList(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST.getValue(), Preconditions.checkNotNull(raw));
  }

  public static StorageId forAccount(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.ACCOUNT.getValue(), Preconditions.checkNotNull(raw));
  }

  public static StorageId forCallLink(byte[] raw) {
    return new StorageId(ManifestRecord.Identifier.Type.CALL_LINK.getValue(), Preconditions.checkNotNull(raw));
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
      if (type != ManifestRecord.Identifier.Type.UNKNOWN && type.getValue() == val) {
        return true;
      }
    }
    return false;
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

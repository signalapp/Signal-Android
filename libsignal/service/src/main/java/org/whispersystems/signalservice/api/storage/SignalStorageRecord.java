package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.util.Arrays;
import java.util.Objects;

public class SignalStorageRecord implements SignalRecord {

  private final byte[]                        key;
  private final int                           type;
  private final Optional<SignalContactRecord> contact;
  private final Optional<SignalGroupV1Record> groupV1;

  public static SignalStorageRecord forContact(SignalContactRecord contact) {
    return forContact(contact.getKey(), contact);
  }

  public static SignalStorageRecord forContact(byte[] key, SignalContactRecord contact) {
    return new SignalStorageRecord(key, StorageRecord.Type.CONTACT_VALUE, Optional.of(contact), Optional.<SignalGroupV1Record>absent());
  }

  public static SignalStorageRecord forGroupV1(SignalGroupV1Record groupV1) {
    return forGroupV1(groupV1.getKey(), groupV1);
  }

  public static SignalStorageRecord forGroupV1(byte[] key, SignalGroupV1Record groupV1) {
    return new SignalStorageRecord(key, StorageRecord.Type.GROUPV1_VALUE, Optional.<SignalContactRecord>absent(), Optional.of(groupV1));
  }

  public static SignalStorageRecord forUnknown(byte[] key, int type) {
    return new SignalStorageRecord(key, type, Optional.<SignalContactRecord>absent(), Optional.<SignalGroupV1Record>absent());
  }

  private SignalStorageRecord(byte[] key,
                              int type,
                              Optional<SignalContactRecord> contact,
                              Optional<SignalGroupV1Record> groupV1)
  {
    this.key     = key;
    this.type    = type;
    this.contact = contact;
    this.groupV1 = groupV1;
  }

  @Override
  public byte[] getKey() {
    return key;
  }

  public int getType() {
    return type;
  }

  public Optional<SignalContactRecord> getContact() {
    return contact;
  }

  public Optional<SignalGroupV1Record> getGroupV1() {
    return groupV1;
  }

  public boolean isUnknown() {
    return !contact.isPresent() && !groupV1.isPresent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalStorageRecord record = (SignalStorageRecord) o;
    return type == record.type &&
        Arrays.equals(key, record.key) &&
        contact.equals(record.contact) &&
        groupV1.equals(record.groupV1);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type, contact, groupV1);
    result = 31 * result + Arrays.hashCode(key);
    return result;
  }
}

package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.util.Arrays;
import java.util.Objects;

public class SignalStorageRecord {

  private final byte[]                        key;
  private final int                           type;
  private final Optional<SignalContactRecord> contact;

  public static SignalStorageRecord forContact(byte[] key, SignalContactRecord contact) {
    return new SignalStorageRecord(key, StorageRecord.Type.CONTACT_VALUE, Optional.of(contact));
  }

  public static SignalStorageRecord forUnknown(byte[] key, int type) {
    return new SignalStorageRecord(key, type, Optional.<SignalContactRecord>absent());
  }

  private SignalStorageRecord(byte key[], int type, Optional<SignalContactRecord> contact) {
    this.key     = key;
    this.type    = type;
    this.contact = contact;
  }

  public byte[] getKey() {
    return key;
  }

  public int getType() {
    return type;
  }

  public Optional<SignalContactRecord> getContact() {
    return contact;
  }

  public boolean isUnknown() {
    return !contact.isPresent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalStorageRecord record = (SignalStorageRecord) o;
    return type == record.type &&
           Arrays.equals(key, record.key) &&
           contact.equals(record.contact);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type, contact);
    result = 31 * result + Arrays.hashCode(key);
    return result;
  }
}

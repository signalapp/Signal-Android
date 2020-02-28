package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Objects;

public class SignalStorageRecord implements SignalRecord {

  private final StorageId id;
  private final Optional<SignalContactRecord> contact;
  private final Optional<SignalGroupV1Record> groupV1;

  public static SignalStorageRecord forContact(SignalContactRecord contact) {
    return forContact(contact.getId(), contact);
  }

  public static SignalStorageRecord forContact(StorageId key, SignalContactRecord contact) {
    return new SignalStorageRecord(key, Optional.of(contact), Optional.<SignalGroupV1Record>absent());
  }

  public static SignalStorageRecord forGroupV1(SignalGroupV1Record groupV1) {
    return forGroupV1(groupV1.getId(), groupV1);
  }

  public static SignalStorageRecord forGroupV1(StorageId key, SignalGroupV1Record groupV1) {
    return new SignalStorageRecord(key, Optional.<SignalContactRecord>absent(), Optional.of(groupV1));
  }

  public static SignalStorageRecord forUnknown(StorageId key) {
    return new SignalStorageRecord(key,Optional.<SignalContactRecord>absent(), Optional.<SignalGroupV1Record>absent());
  }

  private SignalStorageRecord(StorageId id,
                              Optional<SignalContactRecord> contact,
                              Optional<SignalGroupV1Record> groupV1)
  {
    this.id      = id;
    this.contact = contact;
    this.groupV1 = groupV1;
  }

  @Override
  public StorageId getId() {
    return id;
  }

  public int getType() {
    return id.getType();
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
    SignalStorageRecord that = (SignalStorageRecord) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(contact, that.contact) &&
        Objects.equals(groupV1, that.groupV1);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, contact, groupV1);
  }
}

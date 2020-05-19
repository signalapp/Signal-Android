package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;

import java.util.Objects;

public class SignalStorageRecord implements SignalRecord {

  private final StorageId                     id;
  private final Optional<SignalContactRecord> contact;
  private final Optional<SignalGroupV1Record> groupV1;
  private final Optional<SignalGroupV2Record> groupV2;
  private final Optional<SignalAccountRecord> account;

  public static SignalStorageRecord forContact(SignalContactRecord contact) {
    return forContact(contact.getId(), contact);
  }

  public static SignalStorageRecord forContact(StorageId key, SignalContactRecord contact) {
    return new SignalStorageRecord(key, Optional.of(contact), Optional.<SignalGroupV1Record>absent(), Optional.<SignalGroupV2Record>absent(), Optional.<SignalAccountRecord>absent());
  }

  public static SignalStorageRecord forGroupV1(SignalGroupV1Record groupV1) {
    return forGroupV1(groupV1.getId(), groupV1);
  }

  public static SignalStorageRecord forGroupV1(StorageId key, SignalGroupV1Record groupV1) {
    return new SignalStorageRecord(key, Optional.<SignalContactRecord>absent(), Optional.of(groupV1), Optional.<SignalGroupV2Record>absent(), Optional.<SignalAccountRecord>absent());
  }

  public static SignalStorageRecord forGroupV2(SignalGroupV2Record groupV2) {
    return forGroupV2(groupV2.getId(), groupV2);
  }

  public static SignalStorageRecord forGroupV2(StorageId key, SignalGroupV2Record groupV2) {
    return new SignalStorageRecord(key, Optional.<SignalContactRecord>absent(), Optional.<SignalGroupV1Record>absent(), Optional.of(groupV2), Optional.<SignalAccountRecord>absent());
  }

  public static SignalStorageRecord forAccount(SignalAccountRecord account) {
    return forAccount(account.getId(), account);
  }

  public static SignalStorageRecord forAccount(StorageId key, SignalAccountRecord account) {
    return new SignalStorageRecord(key, Optional.<SignalContactRecord>absent(), Optional.<SignalGroupV1Record>absent(), Optional.<SignalGroupV2Record>absent(), Optional.of(account));
  }

  public static SignalStorageRecord forUnknown(StorageId key) {
    return new SignalStorageRecord(key,Optional.<SignalContactRecord>absent(), Optional.<SignalGroupV1Record>absent(), Optional.<SignalGroupV2Record>absent(), Optional.<SignalAccountRecord>absent());
  }

  private SignalStorageRecord(StorageId id,
                              Optional<SignalContactRecord> contact,
                              Optional<SignalGroupV1Record> groupV1,
                              Optional<SignalGroupV2Record> groupV2,
                              Optional<SignalAccountRecord> account)
  {
    this.id      = id;
    this.contact = contact;
    this.groupV1 = groupV1;
    this.groupV2 = groupV2;
    this.account = account;
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

  public Optional<SignalGroupV2Record> getGroupV2() {
    return groupV2;
  }

  public Optional<SignalAccountRecord> getAccount() {
    return account;
  }

  public boolean isUnknown() {
    return !contact.isPresent() && !groupV1.isPresent() && !groupV2.isPresent() && !account.isPresent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalStorageRecord that = (SignalStorageRecord) o;
    return Objects.equals(id, that.id) &&
           Objects.equals(contact, that.contact) &&
           Objects.equals(groupV1, that.groupV1) &&
           Objects.equals(groupV2, that.groupV2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, contact, groupV1, groupV2);
  }
}

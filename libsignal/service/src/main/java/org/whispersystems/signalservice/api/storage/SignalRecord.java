package org.whispersystems.signalservice.api.storage;

public interface SignalRecord {
  StorageId getId();
  SignalStorageRecord asStorageRecord();
  String describeDiff(SignalRecord other);
}

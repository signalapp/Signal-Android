package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.api.storage.SignalRecord;

import java.util.Objects;

/**
 * Represents a pair of records: one old, and one new. The new record should replace the old.
 */
public class StorageRecordUpdate<E extends SignalRecord> {
  private final E oldRecord;
  private final E newRecord;

  StorageRecordUpdate(@NonNull E oldRecord, @NonNull E newRecord) {
    this.oldRecord = oldRecord;
    this.newRecord = newRecord;
  }

  public @NonNull E getOld() {
    return oldRecord;
  }

  public @NonNull E getNew() {
    return newRecord;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StorageRecordUpdate that = (StorageRecordUpdate) o;
    return oldRecord.equals(that.oldRecord) &&
        newRecord.equals(that.newRecord);
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldRecord, newRecord);
  }

  @Override
  public @NonNull String toString() {
    return newRecord.describeDiff(oldRecord);
  }
}

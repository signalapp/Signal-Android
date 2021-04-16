package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.Collection;

/**
 * Handles processing a remote record, which involves applying any local changes that need to be
 * made based on the remote records.
 */
public interface StorageRecordProcessor<E extends SignalRecord> {
  void process(@NonNull Collection<E> remoteRecords, @NonNull StorageKeyGenerator keyGenerator) throws IOException;
}

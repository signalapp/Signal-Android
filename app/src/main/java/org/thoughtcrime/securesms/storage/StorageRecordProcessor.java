package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.Collection;

/**
 * Handles processing a remote record, which involves:
 * - Applying an local changes that need to be made base don the remote record
 * - Returning a result with any remote updates/deletes that need to be applied after merging with
 *   the local record.
 */
public interface StorageRecordProcessor<E extends SignalRecord> {

  @NonNull Result<E> process(@NonNull Collection<E> remoteRecords, @NonNull StorageKeyGenerator keyGenerator) throws IOException;

  final class Result<E extends SignalRecord> {
    private final Collection<StorageRecordUpdate<E>> remoteUpdates;
    private final Collection<E>                      remoteDeletes;
    private final Collection<SignalStorageRecord> localMatches;

    Result(@NonNull Collection<StorageRecordUpdate<E>> remoteUpdates, @NonNull Collection<E> remoteDeletes, @NonNull Collection<E> localMatches) {
      this.remoteDeletes = remoteDeletes;
      this.remoteUpdates = remoteUpdates;
      this.localMatches  = Stream.of(localMatches).map(SignalRecord::asStorageRecord).toList();
    }

    public @NonNull Collection<E> getRemoteDeletes() {
      return remoteDeletes;
    }

    public @NonNull Collection<StorageRecordUpdate<E>> getRemoteUpdates() {
      return remoteUpdates;
    }

    public @NonNull Collection<SignalStorageRecord> getLocalMatches() {
      return localMatches;
    }

    public boolean isLocalOnly() {
      return remoteUpdates.isEmpty() && remoteDeletes.isEmpty();
    }

    @Override
    public @NonNull String toString() {
      if (isLocalOnly()) {
        return "Empty";
      }

      StringBuilder builder = new StringBuilder();

      builder.append(remoteDeletes.size()).append(" Deletes, ").append(remoteUpdates.size()).append(" Updates\n");

      for (StorageRecordUpdate<E> update : remoteUpdates) {
        builder.append("- ").append(update.toString()).append("\n");
      }

      return super.toString();
    }
  }
}

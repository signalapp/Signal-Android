package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.network.util.Preconditions;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merging together recipients and threads is messy business. We can easily replace *almost* all of
 * the references, but there are specific places (notably reactions, jobs, etc) that are really
 * expensive to address. For these cases, we keep mappings of old IDs to new ones to use as a
 * fallback.
 *
 * There should be very few of these, so we keep them in a fast, lazily-loaded memory cache.
 *
 * One important thing to note is that this class will often be accessed inside of database
 * transactions. As a result, it cannot attempt to acquire a database lock while holding a
 * separate lock. Instead, we use the database lock itself as a locking mechanism.
 */
class RemappedRecords {

  private static final String TAG = Log.tag(RemappedRecords.class);

  private static final RemappedRecords INSTANCE = new RemappedRecords();

  private volatile Map<RecipientId, RecipientId> recipientMap;
  private volatile Map<Long, Long>               threadMap;

  private final AtomicBoolean staleTrimScheduled = new AtomicBoolean(false);

  private RemappedRecords() {}

  static RemappedRecords getInstance() {
    return INSTANCE;
  }

  @NonNull Optional<RecipientId> getRecipient(@NonNull RecipientId oldId) {
    ensureRecipientMapIsPopulated();
    return Optional.ofNullable(recipientMap.get(oldId));
  }

  @NonNull Optional<Long> getThread(long oldId) {
    ensureThreadMapIsPopulated();
    return Optional.ofNullable(threadMap.get(oldId));
  }

  void deleteThread(long oldId) {
    ensureInTransaction();
    ensureThreadMapIsPopulated();
    threadMap.remove(oldId);
    SignalDatabase.remappedRecords().deleteThreadMapping(oldId);
  }

  boolean areAnyRemapped(@NonNull Collection<RecipientId> recipientIds) {
    ensureRecipientMapIsPopulated();
    return recipientIds.stream().anyMatch(id -> recipientMap.containsKey(id));
  }

  @NonNull String buildRemapDescription(@NonNull Collection<RecipientId> recipientIds) {
    StringBuilder builder = new StringBuilder();

    for (RecipientId original : recipientIds) {
      if (recipientMap.containsKey(original)) {
        builder.append(original).append(" -> ").append(recipientMap.get(original)).append(" ");
      }
    }

    return builder.toString();
  }

  /**
   * Can only be called inside of a transaction.
   */
  void addRecipient(@NonNull RecipientId oldId, @NonNull RecipientId newId) {
    Log.w(TAG, "[Recipient] Remapping " + oldId + " to " + newId);
    Preconditions.checkArgument(!oldId.equals(newId), "Cannot remap an ID to the same thing!");
    ensureInTransaction();
    ensureRecipientMapIsPopulated();
    recipientMap.put(oldId, newId);
    SignalDatabase.remappedRecords().addRecipientMapping(oldId, newId);
  }

  /**
   * Can only be called inside of a transaction.
   */
  void addThread(long oldId, long newId) {
    Log.w(TAG, "[Thread] Remapping " + oldId + " to " + newId);
    Preconditions.checkArgument(oldId != newId, "Cannot remap an ID to the same thing!");
    ensureInTransaction();
    ensureThreadMapIsPopulated();
    threadMap.put(oldId, newId);
    SignalDatabase.remappedRecords().addThreadMapping(oldId, newId);
  }

  /**
   * Clears out the memory cache. The next read will pull values from disk.
   */
  void resetCache() {
    recipientMap = null;
  }

  private void ensureRecipientMapIsPopulated() {
    if (recipientMap == null) {
      Map<RecipientId, RecipientId> loaded = SignalDatabase.remappedRecords().getAllRecipientMappings();
      synchronized (this) {
        if (recipientMap == null) {
          recipientMap = loaded;
        }
      }
      scheduleStaleTrimIfNeeded(loaded.isEmpty());
    }
  }

  private void ensureThreadMapIsPopulated() {
    if (threadMap == null) {
      Map<Long, Long> loaded = SignalDatabase.remappedRecords().getAllThreadMappings();
      synchronized (this) {
        if (threadMap == null) {
          threadMap = loaded;
        }
      }
      scheduleStaleTrimIfNeeded(loaded.isEmpty());
    }
  }

  private void scheduleStaleTrimIfNeeded(boolean loadedMapWasEmpty) {
    if (!loadedMapWasEmpty && staleTrimScheduled.compareAndSet(false, true)) {
      SignalExecutors.BOUNDED.execute(() -> SignalDatabase.remappedRecords().trimStaleMappings());
    }
  }

  private void ensureInTransaction() {
    if (!SignalDatabase.inTransaction()) {
      throw new IllegalStateException("Must be in a transaction!");
    }
  }
}

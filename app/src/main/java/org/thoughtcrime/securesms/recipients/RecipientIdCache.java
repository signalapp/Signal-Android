package org.thoughtcrime.securesms.recipients;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread safe cache that allows faster looking up of {@link RecipientId}s without hitting the database.
 */
final class RecipientIdCache {

  private static final int INSTANCE_CACHE_LIMIT = 1000;

  static final RecipientIdCache INSTANCE = new RecipientIdCache(INSTANCE_CACHE_LIMIT);

  private static final String TAG = Log.tag(RecipientIdCache.class);

  private final Map<Object, RecipientId> ids;

  RecipientIdCache(int limit) {
    ids = new LinkedHashMap<Object, RecipientId>(128, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Object, RecipientId> eldest) {
        return size() > limit;
      }
    };
  }

  synchronized void put(@NonNull Recipient recipient) {
    RecipientId      recipientId = recipient.getId();
    Optional<String> e164        = recipient.getE164();
    Optional<UUID>   uuid        = recipient.getUuid();

    if (e164.isPresent()) {
      ids.put(e164.get(), recipientId);
    }

    if (uuid.isPresent()) {
      ids.put(uuid.get(), recipientId);
    }
  }

  synchronized @Nullable RecipientId get(@Nullable UUID uuid, @Nullable String e164) {
    if (uuid != null && e164 != null) {
      RecipientId recipientIdByUuid = ids.get(uuid);
      if (recipientIdByUuid == null) return null;

      RecipientId recipientIdByE164 = ids.get(e164);
      if (recipientIdByE164 == null) return null;

      if (recipientIdByUuid.equals(recipientIdByE164)) {
        return recipientIdByUuid;
      } else {
        ids.remove(uuid);
        ids.remove(e164);
        Log.w(TAG, "Seen invalid RecipientIdCacheState");
        return null;
      }
    } else if (uuid != null) {
      return ids.get(uuid);
    } else if (e164 != null) {
      return ids.get(e164);
    }

    return null;
  }

  synchronized void clear() {
    ids.clear();
  }
}

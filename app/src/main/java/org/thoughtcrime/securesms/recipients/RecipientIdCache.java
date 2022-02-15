package org.thoughtcrime.securesms.recipients;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;

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
    Optional<ACI>    aci         = recipient.getAci();

    if (e164.isPresent()) {
      ids.put(e164.get(), recipientId);
    }

    if (aci.isPresent()) {
      ids.put(aci.get(), recipientId);
    }
  }

  synchronized @Nullable RecipientId get(@Nullable ACI aci, @Nullable String e164) {
    if (aci != null && e164 != null) {
      RecipientId recipientIdByAci = ids.get(aci);
      if (recipientIdByAci == null) return null;

      RecipientId recipientIdByE164 = ids.get(e164);
      if (recipientIdByE164 == null) return null;

      if (recipientIdByAci.equals(recipientIdByE164)) {
        return recipientIdByAci;
      } else {
        ids.remove(aci);
        ids.remove(e164);
        Log.w(TAG, "Seen invalid RecipientIdCacheState");
        return null;
      }
    } else if (aci != null) {
      return ids.get(aci);
    } else if (e164 != null) {
      return ids.get(e164);
    }

    return null;
  }

  synchronized void clear() {
    ids.clear();
  }
}

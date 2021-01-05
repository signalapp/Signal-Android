package org.thoughtcrime.securesms.recipients;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.MissingRecipientException;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LiveRecipientCache {

  private static final String TAG = Log.tag(LiveRecipientCache.class);

  private static final int CACHE_MAX      = 1000;
  private static final int CACHE_WARM_MAX = 500;

  private static final Object SELF_LOCK = new Object();

  private final Context                         context;
  private final RecipientDatabase               recipientDatabase;
  private final Map<RecipientId, LiveRecipient> recipients;
  private final LiveRecipient                   unknown;

  @GuardedBy("SELF_LOCK")
  private RecipientId localRecipientId;
  private boolean     warmedUp;

  @SuppressLint("UseSparseArrays")
  public LiveRecipientCache(@NonNull Context context) {
    this.context           = context.getApplicationContext();
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.recipients        = new LRUCache<>(CACHE_MAX);
    this.unknown           = new LiveRecipient(context, new MutableLiveData<>(), Recipient.UNKNOWN);
  }

  @AnyThread
  synchronized @NonNull LiveRecipient getLive(@NonNull RecipientId id) {
    if (id.isUnknown()) return unknown;

    LiveRecipient live = recipients.get(id);

    if (live == null) {
      final LiveRecipient newLive = new LiveRecipient(context, new MutableLiveData<>(), new Recipient(id));

      recipients.put(id, newLive);

      MissingRecipientException prettyStackTraceError = new MissingRecipientException(newLive.getId());

      SignalExecutors.BOUNDED.execute(() -> {
        try {
          newLive.resolve();
        } catch (MissingRecipientException e) {
          throw prettyStackTraceError;
        }
      });

      live = newLive;
    }

    return live;
  }

  /**
   * Adds a recipient to the cache if we don't have an entry. This will also update a cache entry
   * if the provided recipient is resolved, or if the existing cache entry is unresolved.
   *
   * If the recipient you add is unresolved, this will enqueue a resolve on a background thread.
   */
  @AnyThread
  public synchronized void addToCache(@NonNull Collection<Recipient> newRecipients) {
    for (Recipient recipient : newRecipients) {
      LiveRecipient live         = recipients.get(recipient.getId());
      boolean       needsResolve = false;

      if (live == null) {
        live = new LiveRecipient(context, new MutableLiveData<>(), recipient);
        recipients.put(recipient.getId(), live);
        needsResolve = recipient.isResolving();
      } else if (live.get().isResolving() || !recipient.isResolving()) {
        live.set(recipient);
        needsResolve = recipient.isResolving();
      }

      if (needsResolve) {
        MissingRecipientException prettyStackTraceError = new MissingRecipientException(recipient.getId());
        SignalExecutors.BOUNDED.execute(() -> {
          try {
            recipient.resolve();
          } catch (MissingRecipientException e) {
            throw prettyStackTraceError;
          }
        });
      }
    }
  }

  @NonNull Recipient getSelf() {
    synchronized (SELF_LOCK) {
      if (localRecipientId == null) {
        UUID   localUuid = TextSecurePreferences.getLocalUuid(context);
        String localE164 = TextSecurePreferences.getLocalNumber(context);

        if (localUuid != null) {
          localRecipientId = recipientDatabase.getByUuid(localUuid).or(recipientDatabase.getByE164(localE164)).orNull();
        } else if (localE164 != null) {
          localRecipientId = recipientDatabase.getByE164(localE164).orNull();
        } else {
          throw new IllegalStateException("Tried to call getSelf() before local data was set!");
        }

        if (localRecipientId == null) {
          throw new MissingRecipientException(localRecipientId);
        }
      }
    }

    return getLive(localRecipientId).resolve();
  }

  @AnyThread
  public synchronized void warmUp() {
    if (warmedUp) {
      return;
    } else {
      warmedUp = true;
    }

    SignalExecutors.BOUNDED.execute(() -> {
      ThreadDatabase  threadDatabase = DatabaseFactory.getThreadDatabase(context);
      List<Recipient> recipients     = new ArrayList<>();

      try (ThreadDatabase.Reader reader = threadDatabase.readerFor(threadDatabase.getRecentConversationList(CACHE_WARM_MAX, false, false))) {
        int          i      = 0;
        ThreadRecord record = null;

        while ((record = reader.getNext()) != null && i < CACHE_WARM_MAX) {
          recipients.add(record.getRecipient());
          i++;
        }
      }

      Log.d(TAG, "Warming up " + recipients.size() + " recipients.");
      addToCache(recipients);
    });
  }

  @AnyThread
  public synchronized void clearSelf() {
    localRecipientId = null;
  }

  @AnyThread
  public synchronized void clear() {
    recipients.clear();
  }
}

package org.thoughtcrime.securesms.recipients;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.MissingRecipientError;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LiveRecipientCache {

  private static final String TAG = Log.tag(LiveRecipientCache.class);

  private static final int CACHE_MAX      = 1000;
  private static final int CACHE_WARM_MAX = 500;

  private final Context                         context;
  private final RecipientDatabase               recipientDatabase;
  private final Map<RecipientId, LiveRecipient> recipients;
  private final LiveRecipient                   unknown;

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

      MissingRecipientError prettyStackTraceError = new MissingRecipientError(newLive.getId());

      SignalExecutors.BOUNDED.execute(() -> {
        try {
          newLive.resolve();
        } catch (MissingRecipientError e) {
          throw prettyStackTraceError;
        }
      });

      live = newLive;
    }

    return live;
  }

  @NonNull Recipient getSelf() {
    synchronized (this) {
      if (localRecipientId == null) {
        UUID   localUuid = TextSecurePreferences.getLocalUuid(context);
        String localE164 = TextSecurePreferences.getLocalNumber(context);

        if (localUuid != null) {
          localRecipientId = recipientDatabase.getByUuid(localUuid).or(recipientDatabase.getByE164(localE164)).orNull();
        } else if (localE164 != null) {
          localRecipientId = recipientDatabase.getByE164(localE164).orNull();
        } else {
          throw new AssertionError("Tried to call getSelf() before local data was set!");
        }

        if (localRecipientId == null) {
          throw new MissingRecipientError(localRecipientId);
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
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);

      try (ThreadDatabase.Reader reader = threadDatabase.readerFor(threadDatabase.getConversationList())) {
        int             i          = 0;
        ThreadRecord    record     = null;
        List<Recipient> recipients = new ArrayList<>();

        while ((record = reader.getNext()) != null && i < CACHE_WARM_MAX) {
          recipients.add(record.getRecipient());
          i++;
        }

        Log.d(TAG, "Warming up " + recipients.size() + " recipients.");

        Collections.reverse(recipients);
        Stream.of(recipients).map(Recipient::getId).forEach(this::getLive);
      }
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

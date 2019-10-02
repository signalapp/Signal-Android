package org.thoughtcrime.securesms.recipients;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.MissingRecipientError;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Map;

public final class LiveRecipientCache {


  private final Context                         context;
  private final RecipientDatabase               recipientDatabase;
  private final Map<RecipientId, LiveRecipient> recipients;
  private final LiveRecipient                   unknown;

  private RecipientId localRecipientId;

  @SuppressLint("UseSparseArrays")
  public LiveRecipientCache(@NonNull Context context) {
    this.context           = context.getApplicationContext();
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.recipients        = new LRUCache<>(1000);
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
        localRecipientId = recipientDatabase.getOrInsertFromE164(TextSecurePreferences.getLocalNumber(context));
      }
    }

    return getLive(localRecipientId).resolve();
  }
}

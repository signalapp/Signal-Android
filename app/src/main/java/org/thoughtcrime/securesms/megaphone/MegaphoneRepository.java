package org.thoughtcrime.securesms.megaphone;

import android.app.Application;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MegaphoneDatabase;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.megaphone.Megaphones.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Synchronization of data structures is done using a serial executor. Do not access or change
 * data structures or fields on anything except the executor.
 */
public class MegaphoneRepository {

  private final Application                 context;
  private final Executor                    executor;
  private final MegaphoneDatabase           database;
  private final Map<Event, MegaphoneRecord> databaseCache;

  private boolean enabled;

  public MegaphoneRepository(@NonNull Application context) {
    this.context       = context;
    this.executor      = SignalExecutors.SERIAL;
    this.database      = MegaphoneDatabase.getInstance(context);
    this.databaseCache = new HashMap<>();

    executor.execute(this::init);
  }

  /**
   * Marks any megaphones a new user shouldn't see as "finished".
   */
  @AnyThread
  public void onFirstEverAppLaunch() {
    executor.execute(() -> {
      database.markFinished(Event.REACTIONS);
      database.markFinished(Event.MESSAGE_REQUESTS);
      database.markFinished(Event.LINK_PREVIEWS);
      database.markFinished(Event.RESEARCH);
      database.markFinished(Event.GROUP_CALLING);
      resetDatabaseCache();
    });
  }

  @AnyThread
  public void onAppForegrounded() {
    executor.execute(() -> enabled = true);
  }

  @AnyThread
  public void getNextMegaphone(@NonNull Callback<Megaphone> callback) {
    executor.execute(() -> {
      if (enabled) {
        init();
        callback.onResult(Megaphones.getNextMegaphone(context, databaseCache));
      } else {
        callback.onResult(null);
      }
    });
  }

  @AnyThread
  public void markVisible(@NonNull Megaphones.Event event) {
    long time = System.currentTimeMillis();

    executor.execute(() -> {
      if (getRecord(event).getFirstVisible() == 0) {
        database.markFirstVisible(event, time);
        resetDatabaseCache();
      }
    });
  }

  @AnyThread
  public void markSeen(@NonNull Event event) {
    long lastSeen = System.currentTimeMillis();

    executor.execute(() -> {
      MegaphoneRecord record = getRecord(event);
      database.markSeen(event, record.getSeenCount() + 1, lastSeen);
      enabled = false;
      resetDatabaseCache();
    });
  }

  @AnyThread
  public void markFinished(@NonNull Event event) {
    markFinished(event, null);
  }

  @AnyThread
  public void markFinished(@NonNull Event event, @Nullable Runnable onComplete) {
    executor.execute(() -> {
      MegaphoneRecord record = databaseCache.get(event);
      if (record != null && record.isFinished()) {
        return;
      }

      database.markFinished(event);
      resetDatabaseCache();

      if (onComplete != null) {
        onComplete.run();
      }
    });
  }

  @WorkerThread
  private void init() {
    List<MegaphoneRecord> records = database.getAllAndDeleteMissing();
    Set<Event>            events  = Stream.of(records).map(MegaphoneRecord::getEvent).collect(Collectors.toSet());
    Set<Event>            missing = Stream.of(Megaphones.Event.values()).filterNot(events::contains).collect(Collectors.toSet());

    database.insert(missing);
    resetDatabaseCache();
  }

  @WorkerThread
  private @NonNull MegaphoneRecord getRecord(@NonNull Event event) {
    //noinspection ConstantConditions
    return databaseCache.get(event);
  }

  @WorkerThread
  private void resetDatabaseCache() {
    databaseCache.clear();
    databaseCache.putAll(Stream.of(database.getAllAndDeleteMissing()).collect(Collectors.toMap(MegaphoneRecord::getEvent, m -> m)));
  }

  public interface Callback<E> {
    void onResult(E result);
  }
}

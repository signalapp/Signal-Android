package org.thoughtcrime.securesms.keyvalue;

import android.app.Application;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.KeyValueDatabase;
import org.thoughtcrime.securesms.util.SignalUncaughtExceptionHandler;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * An replacement for {@link android.content.SharedPreferences} that stores key-value pairs in our
 * encrypted database.
 *
 * Implemented as a write-through cache that is safe to read and write to on the main thread.
 *
 * Writes are enqueued on a separate executor, but writes are finished up in
 * {@link SignalUncaughtExceptionHandler}, meaning all write should finish barring a native crash
 * or the system killing us unexpectedly (i.e. a force-stop).
 */
public final class KeyValueStore implements KeyValueReader {

  private static final String TAG = Log.tag(KeyValueStore.class);

  private final ExecutorService  executor;
  private final KeyValueDatabase database;

  private KeyValueDataSet dataSet;

  public KeyValueStore(@NonNull Application application) {
    this.executor = SignalExecutors.newCachedSingleThreadExecutor("signal-KeyValueStore");
    this.database = KeyValueDatabase.getInstance(application);
  }

  @AnyThread
  @Override
  public synchronized byte[] getBlob(@NonNull String key, byte[] defaultValue) {
    initializeIfNecessary();
    return dataSet.getBlob(key, defaultValue);
  }

  @AnyThread
  @Override
  public synchronized boolean getBoolean(@NonNull String key, boolean defaultValue) {
    initializeIfNecessary();
    return dataSet.getBoolean(key, defaultValue);
  }

  @AnyThread
  @Override
  public synchronized float getFloat(@NonNull String key, float defaultValue) {
    initializeIfNecessary();
    return dataSet.getFloat(key, defaultValue);
  }

  @AnyThread
  @Override
  public synchronized int getInteger(@NonNull String key, int defaultValue) {
    initializeIfNecessary();
    return dataSet.getInteger(key, defaultValue);
  }

  @AnyThread
  @Override
  public synchronized long getLong(@NonNull String key, long defaultValue) {
    initializeIfNecessary();
    return dataSet.getLong(key, defaultValue);
  }

  @AnyThread
  @Override
  public synchronized String getString(@NonNull String key, String defaultValue) {
    initializeIfNecessary();
    return dataSet.getString(key, defaultValue);
  }

  /**
   * @return A writer that allows writing and removing multiple entries in a single atomic
   *         transaction.
   */
  @AnyThread
  @NonNull Writer beginWrite() {
    return new Writer();
  }

  /**
   * @return A reader that lets you read from an immutable snapshot of the store, ensuring that data
   *         is consistent between reads. If you're only reading a single value, it is more
   *         efficient to use the various get* methods instead.
   */
  @AnyThread
  synchronized @NonNull KeyValueReader beginRead() {
    initializeIfNecessary();

    KeyValueDataSet copy = new KeyValueDataSet();
    copy.putAll(dataSet);

    return copy;
  }

  /**
   * Ensures that any pending writes (such as those made via {@link Writer#apply()}) are finished.
   */
  @AnyThread
  synchronized void blockUntilAllWritesFinished() {
    CountDownLatch latch = new CountDownLatch(1);

    executor.execute(latch::countDown);

    try {
      latch.await();
    } catch (InterruptedException e) {
      Log.w(TAG, "Failed to wait for all writes.");
    }
  }


  private synchronized void write(@NonNull KeyValueDataSet newDataSet, @NonNull Collection<String> removes) {
    initializeIfNecessary();

    dataSet.putAll(newDataSet);
    dataSet.removeAll(removes);

    executor.execute(() -> database.writeDataSet(newDataSet, removes));
  }

  private void initializeIfNecessary() {
    if (dataSet != null) return;
    this.dataSet = database.getDataSet();
  }

  class Writer {
    private final KeyValueDataSet dataSet = new KeyValueDataSet();
    private final Set<String>     removes = new HashSet<>();

    @NonNull Writer putBlob(@NonNull String key, @Nullable byte[] value) {
      dataSet.putBlob(key, value);
      return this;
    }

    @NonNull Writer putBoolean(@NonNull String key, boolean value) {
      dataSet.putBoolean(key, value);
      return this;
    }

    @NonNull Writer putFloat(@NonNull String key, float value) {
      dataSet.putFloat(key, value);
      return this;
    }

    @NonNull Writer putInteger(@NonNull String key, int value) {
      dataSet.putInteger(key, value);
      return this;
    }

    @NonNull Writer putLong(@NonNull String key, long value) {
      dataSet.putLong(key, value);
      return this;
    }

    @NonNull Writer putString(@NonNull String key, String value) {
      dataSet.putString(key, value);
      return this;
    }

    @NonNull Writer remove(@NonNull String key) {
      removes.add(key);
      return this;
    }

    @AnyThread
    void apply() {
      for (String key : removes) {
        if (dataSet.containsKey(key)) {
          throw new IllegalStateException("Tried to remove a key while also setting it!");
        }
      }

      write(dataSet, removes);
    }

    @WorkerThread
    void commit() {
      apply();
      blockUntilAllWritesFinished();
    }
  }
}

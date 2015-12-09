package org.thoughtcrime.securesms.database;

import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.util.LRUCache;

public class EarlyReceiptCache {

  private static final String TAG = EarlyReceiptCache.class.getSimpleName();

  private final LRUCache<Placeholder, Long> cache = new LRUCache<>(100);

  public synchronized void increment(long timestamp, String address) {
    Log.w(TAG, this+"");
    Log.w(TAG, String.format("Early receipt: %d,%s", timestamp, address));
    Placeholder tuple = new Placeholder(timestamp, address);
    Long        count = cache.get(tuple);

    if (count != null) {
      cache.put(tuple, ++count);
    } else {
      cache.put(tuple, 1L);
    }
  }

  public synchronized long remove(long timestamp, String address) {
    Long count = cache.remove(new Placeholder(timestamp, address));
    Log.w(TAG, this+"");
    Log.w(TAG, String.format("Checking early receipts (%d, %s): %d", timestamp, address, count));
    return count != null ? count : 0;
  }

  private class Placeholder {

    private final          long   timestamp;
    private final @NonNull String address;

    private Placeholder(long timestamp, @NonNull  String address) {
      this.timestamp = timestamp;
      this.address   = address;
    }

    @Override
    public boolean equals(Object other) {
      return other != null && other instanceof Placeholder &&
          ((Placeholder)other).timestamp == this.timestamp &&
          ((Placeholder)other).address.equals(this.address);
    }

    @Override
    public int hashCode() {
      return (int)timestamp ^ address.hashCode();
    }
  }
}

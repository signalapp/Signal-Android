package org.thoughtcrime.securesms.util;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Useful for generate ID's to be used with
 * {@link android.support.v7.widget.RecyclerView.Adapter#getItemId(int)} when you otherwise don't
 * have a good way to generate an ID.
 */
public class StableIdGenerator<E> {

  private final Map<E, Long> keys = new HashMap<>();

  private long index = 1;

  @MainThread
  public long getId(@NonNull E item) {
    if (keys.containsKey(item)) {
      return keys.get(item);
    }

    long key = index++;
    keys.put(item, key);

    return key;
  }
}

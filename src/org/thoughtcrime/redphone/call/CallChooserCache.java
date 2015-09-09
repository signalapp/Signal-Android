package org.thoughtcrime.redphone.call;

import android.support.v4.util.LruCache;

public class CallChooserCache {

  private final long CHOICE_CACHED_INTERVAL = 10 * 1000;

  private static final CallChooserCache instance = new CallChooserCache();

  public static CallChooserCache getInstance() {
    return instance;
  }

  private final LruCache<String, Long> insecureChoices = new LruCache<String, Long>(2);

  private CallChooserCache() {}

  public synchronized void addInsecureChoice(String number) {
    insecureChoices.put(number, System.currentTimeMillis());
  }

  public synchronized boolean isRecentInsecureChoice(String number) {
    Long timestamp = insecureChoices.get(number);

    if (timestamp == null)
      return false;

    return System.currentTimeMillis() - timestamp < CHOICE_CACHED_INTERVAL;
  }
}

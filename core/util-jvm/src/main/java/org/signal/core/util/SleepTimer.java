package org.signal.core.util;

public interface SleepTimer {
  void sleep(long millis) throws InterruptedException;
}

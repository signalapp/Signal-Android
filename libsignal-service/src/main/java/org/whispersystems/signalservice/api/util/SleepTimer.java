package org.whispersystems.signalservice.api.util;

public interface SleepTimer {
  public void sleep(long millis) throws InterruptedException;
}

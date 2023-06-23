package org.thoughtcrime.securesms.crypto;

import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of {@link SignalSessionLock} that is backed by a {@link ReentrantLock}.
 */
public enum ReentrantSessionLock implements SignalSessionLock {

  INSTANCE;

  private static final ReentrantLock LOCK = new ReentrantLock();

  @Override
  public Lock acquire() {
    LOCK.lock();
    return LOCK::unlock;
  }

  public boolean isHeldByCurrentThread() {
    return LOCK.isHeldByCurrentThread();
  }
}

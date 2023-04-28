package org.thoughtcrime.securesms.groups;

import androidx.annotation.WorkerThread;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class GroupsV2ProcessingLock {

  private static final String TAG = Log.tag(GroupsV2ProcessingLock.class);

  private GroupsV2ProcessingLock() {
  }

  private static final Lock lock = new ReentrantLock();

  @WorkerThread
  static Closeable acquireGroupProcessingLock() throws GroupChangeBusyException {
    return acquireGroupProcessingLock(5000);
  }

  @WorkerThread
  static Closeable acquireGroupProcessingLock(long timeoutMs) throws GroupChangeBusyException {
    ThreadUtil.assertNotMainThread();

    try {
      if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw new GroupChangeBusyException("Failed to get a lock on the group processing in the timeout period");
      }
      return lock::unlock;
    } catch (InterruptedException e) {
      Log.w(TAG, e);
      throw new GroupChangeBusyException(e);
    }
  }
}

package org.thoughtcrime.securesms.groups;

import androidx.annotation.WorkerThread;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.util.RemoteConfig;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class GroupsV2ProcessingLock {

  private static final String TAG = Log.tag(GroupsV2ProcessingLock.class);

  private GroupsV2ProcessingLock() {
  }

  private static final GroupReentrantLock lock = new GroupReentrantLock();

  @WorkerThread
  public static Closeable acquireGroupProcessingLock() throws GroupChangeBusyException {
    if (RemoteConfig.internalUser()) {
      if (!lock.isHeldByCurrentThread()) {
        if (SignalDatabase.inTransaction()) {
          throw new AssertionError("Tried to acquire the group lock inside of a database transaction!");
        }
        if (ReentrantSessionLock.INSTANCE.isHeldByCurrentThread()) {
          throw new AssertionError("Tried to acquire the group lock inside of the ReentrantSessionLock!!");
        }
      }
    }
    return acquireGroupProcessingLock(5000);
  }

  @WorkerThread
  private static Closeable acquireGroupProcessingLock(long timeoutMs) throws GroupChangeBusyException {
    ThreadUtil.assertNotMainThread();

    try {
      if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw new GroupChangeBusyException("Failed to get a lock on the group processing in the timeout period. Owner: " + lock.getOwnerName());
      }
      return lock::unlock;
    } catch (InterruptedException e) {
      Log.w(TAG, e);
      throw new GroupChangeBusyException(e);
    }
  }

  private static class GroupReentrantLock extends ReentrantLock {
    String getOwnerName() {
      Thread owner = super.getOwner();
      return (owner != null) ? owner.getName() : "null";
    }
  }
}
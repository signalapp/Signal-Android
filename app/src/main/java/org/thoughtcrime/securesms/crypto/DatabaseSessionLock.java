package org.thoughtcrime.securesms.crypto;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.whispersystems.signalservice.api.SignalSessionLock;

/**
 * An implementation of {@link SignalSessionLock} that effectively re-uses our database lock.
 */
public enum DatabaseSessionLock implements SignalSessionLock {

  INSTANCE;

  public static final long NO_OWNER = -1;

  private volatile long ownerThreadId = NO_OWNER;

  @Override
  public Lock acquire() {
    SQLiteDatabase db = DatabaseFactory.getInstance(ApplicationDependencies.getApplication()).getRawDatabase();

    if (db.isDbLockedByCurrentThread()) {
      return () -> {};
    }

    db.beginTransaction();

    ownerThreadId = Thread.currentThread().getId();

    return () -> {
      ownerThreadId = -1;
      db.setTransactionSuccessful();
      db.endTransaction();
    };
  }

  /**
   * Important: Only truly useful for debugging. Do not rely on this for functionality. There's tiny
   * windows where this state might not be fully accurate.
   *
   * @return True if it's likely that some other thread owns this lock, and it's not you.
   */
  public boolean isLikelyHeldByOtherThread() {
    long ownerThreadId = this.ownerThreadId;
    return ownerThreadId != -1 && ownerThreadId == Thread.currentThread().getId();
  }

  /**
   * Important: Only truly useful for debugging. Do not rely on this for functionality. There's a
   * tiny window where a thread may still own the lock, but the state we track around it has been
   * cleared.
   *
   * @return The ID of the thread that likely owns this lock, or {@link #NO_OWNER} if no one owns it.
   */
  public long getLikeyOwnerThreadId() {
    return ownerThreadId;
  }
}

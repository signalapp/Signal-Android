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

  @Override
  public Lock acquire() {
    SQLiteDatabase db = DatabaseFactory.getInstance(ApplicationDependencies.getApplication()).getRawDatabase();

    if (db.isDbLockedByCurrentThread()) {
      return () -> {};
    }

    db.beginTransaction();

    return () -> {
      db.setTransactionSuccessful();
      db.endTransaction();
    };
  }
}

package org.thoughtcrime.securesms.database;

import android.content.Context;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.Closeable;

public final class DatabaseLock {

  public static @NonNull Lock acquire(@NonNull Context context) {
    SQLiteDatabase db = DatabaseFactory.getInstance(context).getRawDatabase();

    if (db.isDbLockedByCurrentThread()) {
      return () -> {};
    }

    db.beginTransaction();

    return () -> {
      db.setTransactionSuccessful();
      db.endTransaction();
    };
  }

  public interface Lock extends Closeable {
    @Override
    void close();
  }
}

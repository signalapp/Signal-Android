package org.thoughtcrime.securesms.database;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * Simple interface for common methods across our various
 * {@link net.sqlcipher.database.SQLiteOpenHelper}s.
 */
public interface SignalDatabase {
  SQLiteDatabase getSqlCipherDatabase();
  String getDatabaseName();
}

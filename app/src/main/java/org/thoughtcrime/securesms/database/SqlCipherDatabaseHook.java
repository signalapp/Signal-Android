package org.thoughtcrime.securesms.database;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

/**
 * Standard hook for setting common SQLCipher PRAGMAs.
 */
public class SqlCipherDatabaseHook implements SQLiteDatabaseHook {

  @Override
  public void preKey(SQLiteDatabase db) {
    db.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
    db.rawExecSQL("PRAGMA cipher_default_page_size = 4096;");
  }

  @Override
  public void postKey(SQLiteDatabase db) {
    db.rawExecSQL("PRAGMA cipher_compatibility = 3;");
    db.rawExecSQL("PRAGMA cipher_memory_security = OFF;");
    db.rawExecSQL("PRAGMA kdf_iter = '1';");
    db.rawExecSQL("PRAGMA cipher_page_size = 4096;");
    db.enableWriteAheadLogging();
    db.setForeignKeyConstraintsEnabled(true);
  }
}

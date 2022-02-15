package org.thoughtcrime.securesms.database;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

/**
 * Standard hook for setting common SQLCipher PRAGMAs.
 */
public class SqlCipherDatabaseHook implements SQLiteDatabaseHook {

  @Override
  public void preKey(SQLiteConnection connection) {
    connection.execute("PRAGMA cipher_default_kdf_iter = 1;", null, null);
    connection.execute("PRAGMA cipher_default_page_size = 4096;", null, null);
  }

  @Override
  public void postKey(SQLiteConnection connection) {
    connection.execute("PRAGMA cipher_compatibility = 3;", null, null);
    connection.execute("PRAGMA kdf_iter = '1';", null, null);
    connection.execute("PRAGMA cipher_page_size = 4096;", null, null);
  }
}

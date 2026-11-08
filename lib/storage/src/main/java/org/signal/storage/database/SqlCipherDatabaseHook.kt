/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.database

import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook

/**
 * Standard hook for setting common SQLCipher PRAGMAs.
 */
class SqlCipherDatabaseHook : SQLiteDatabaseHook {

  override fun preKey(connection: SQLiteConnection) {
    connection.execute("PRAGMA cipher_default_kdf_iter = 1;", null, null)
    connection.execute("PRAGMA cipher_default_page_size = 4096;", null, null)
  }

  override fun postKey(connection: SQLiteConnection) {
    connection.execute("PRAGMA cipher_compatibility = 3;", null, null)
    connection.execute("PRAGMA kdf_iter = '1';", null, null)
    connection.execute("PRAGMA cipher_page_size = 4096;", null, null)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.database

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Simple interface for common methods across our various [net.zetetic.database.sqlcipher.SQLiteOpenHelper]s.
 */
interface SignalDatabaseOpenHelper {
  val sqlCipherDatabase: SQLiteDatabase
}

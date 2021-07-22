package org.thoughtcrime.securesms.database

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase

/**
 * A simple wrapper to load SQLCipher libs exactly once. The exact entry point of database access is non-deterministic because content providers run before
 * Application#onCreate().
 */
class SqlCipherLibraryLoader {

  companion object {
    @Volatile
    private var loaded = false
    private val LOCK = Object()

    @JvmStatic
    fun load(context: Context) {
      if (!loaded) {
        synchronized(LOCK) {
          if (!loaded) {
            SQLiteDatabase.loadLibs(context)
            loaded = true
          }
        }
      }
    }
  }
}

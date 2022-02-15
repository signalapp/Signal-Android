package org.thoughtcrime.securesms.database

/**
 * A simple wrapper to load SQLCipher libs exactly once. The exact entry point of database access is non-deterministic because content providers run before
 * Application#onCreate().
 */
object SqlCipherLibraryLoader {
  @Volatile
  private var loaded = false
  private val LOCK = Object()

  @JvmStatic
  fun load() {
    if (!loaded) {
      synchronized(LOCK) {
        if (!loaded) {
          System.loadLibrary("sqlcipher")
          loaded = true
        }
      }
    }
  }
}

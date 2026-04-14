package org.thoughtcrime.securesms.account

import android.app.Application
import org.signal.core.util.logging.Log
import java.io.File

/**
 * Manages per-account directory structure:
 *
 * ```
 * /data/data/org.thoughtcrime.securesms/
 *   account-registry.db
 *   accounts/
 *     account-0/
 *       signal.db
 *       signal-key-value.db
 *       signal-jobmanager.db
 *       attachments/
 *     account-1/
 *       signal.db
 *       signal-key-value.db
 *       signal-jobmanager.db
 *       attachments/
 * ```
 */
object AccountFileManager {

  private val TAG = Log.tag(AccountFileManager::class.java)
  private const val ACCOUNTS_DIR = "accounts"

  /**
   * Returns the root accounts directory, creating it if necessary.
   */
  fun getAccountsRoot(application: Application): File {
    val dir = File(application.filesDir, ACCOUNTS_DIR)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Returns the directory for a specific account, creating it if necessary.
   */
  fun getAccountDir(application: Application, accountId: String): File {
    val dir = File(getAccountsRoot(application), accountId)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Returns the path for a database file within an account's directory.
   * For example: getAccountDatabasePath(app, "account-0", "signal.db")
   */
  fun getAccountDatabasePath(application: Application, accountId: String, databaseName: String): String {
    return File(getAccountDir(application, accountId), databaseName).absolutePath
  }

  /**
   * Returns the attachments directory for a specific account.
   */
  fun getAccountAttachmentsDir(application: Application, accountId: String): File {
    val dir = File(getAccountDir(application, accountId), "attachments")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  /**
   * Migrates existing single-account databases into the account-0 directory.
   * This is a one-time operation on first launch after multi-account support is added.
   *
   * Returns the account ID of the migrated account ("account-0").
   */
  fun migrateExistingDataToAccountDir(application: Application): String {
    val accountId = "account-0"
    val accountDir = getAccountDir(application, accountId)

    val databases = listOf("signal.db", "signal.db-wal", "signal.db-shm",
                           "signal-key-value.db", "signal-key-value.db-wal", "signal-key-value.db-shm",
                           "signal-jobmanager.db", "signal-jobmanager.db-wal", "signal-jobmanager.db-shm")

    for (dbName in databases) {
      val source = application.getDatabasePath(dbName)
      if (source.exists()) {
        val dest = File(accountDir, dbName)
        Log.i(TAG, "Migrating $dbName to ${dest.absolutePath}")
        source.renameTo(dest)
      }
    }

    // Move attachments
    val partsDir = application.getDir("parts", android.content.Context.MODE_PRIVATE)
    if (partsDir.exists() && partsDir.listFiles()?.isNotEmpty() == true) {
      val destParts = File(accountDir, "parts")
      if (!destParts.exists()) {
        Log.i(TAG, "Migrating parts directory")
        partsDir.renameTo(destParts)
      }
    }

    Log.i(TAG, "Migration complete for $accountId")
    return accountId
  }

  /**
   * Creates a fresh directory structure for a new account.
   */
  fun createAccountDir(application: Application, accountId: String): File {
    val dir = getAccountDir(application, accountId)
    Log.i(TAG, "Created account directory: ${dir.absolutePath}")
    return dir
  }

  /**
   * Deletes an account's entire directory (used when removing an account).
   */
  fun deleteAccountDir(application: Application, accountId: String): Boolean {
    val dir = File(getAccountsRoot(application), accountId)
    if (dir.exists()) {
      val deleted = dir.deleteRecursively()
      Log.i(TAG, "Deleted account directory for $accountId: $deleted")
      return deleted
    }
    return false
  }

  /**
   * Returns true if the given account directory has database files in it.
   */
  fun accountHasData(application: Application, accountId: String): Boolean {
    val signalDb = File(getAccountDir(application, accountId), "signal.db")
    return signalDb.exists()
  }
}

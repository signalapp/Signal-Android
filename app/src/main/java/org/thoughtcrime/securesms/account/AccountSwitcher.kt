package org.thoughtcrime.securesms.account

import android.app.Application
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Orchestrates the hot-swap account switching flow. This is the single entry point
 * for switching between Signal accounts in a multi-account setup.
 *
 * The switch sequence:
 * 1. Tear down network connections (WebSocket, API clients)
 * 2. Flush pending work and clear caches
 * 3. Close current account databases
 * 4. Update account registry to reflect new active account
 * 5. Reinitialize all singletons (SignalDatabase, SignalStore, JobDatabase) with new account's paths
 * 6. Reconnect network with new account's credentials
 * 7. Notify UI to rebuild
 */
object AccountSwitcher {

  private val TAG = Log.tag(AccountSwitcher::class.java)

  /**
   * Switches the active account. This must be called from a background thread.
   *
   * @param application The application context
   * @param targetAccountId The account ID to switch to (e.g., "account-1")
   * @param onComplete Optional callback invoked on the main thread after the switch is complete
   */
  @JvmStatic
  fun switchToAccount(application: Application, targetAccountId: String, onComplete: (() -> Unit)? = null) {
    val registry = AccountRegistry.getInstance(application)
    val currentAccount = registry.getActiveAccount()

    if (currentAccount?.accountId == targetAccountId) {
      Log.i(TAG, "Already on account $targetAccountId, no switch needed")
      onComplete?.invoke()
      return
    }

    Log.i(TAG, "Switching from ${currentAccount?.accountId} to $targetAccountId")
    val startTime = System.currentTimeMillis()

    // Step 1: Tear down network and caches
    Log.d(TAG, "Step 1: Tearing down network and caches")
    AppDependencies.resetAllForAccountSwitch()

    // Step 2: Flush SignalStore writes before closing
    Log.d(TAG, "Step 2: Flushing pending writes")
    try {
      SignalStore.blockUntilAllWritesFinished()
    } catch (e: Exception) {
      Log.w(TAG, "Error flushing writes", e)
    }

    // Step 3: Update account registry
    Log.d(TAG, "Step 3: Updating account registry")
    registry.setActiveAccount(targetAccountId)

    // Step 4: Reinitialize databases with new account's paths
    Log.d(TAG, "Step 4: Reinitializing databases")
    reinitDatabases(application, targetAccountId)

    // Step 5: Reconnect network with new credentials
    Log.d(TAG, "Step 5: Reconnecting network")
    AppDependencies.startNetwork()

    // Step 6: Re-warm caches
    Log.d(TAG, "Step 6: Re-warming caches")
    AppDependencies.recipientCache.warmUp()

    val elapsed = System.currentTimeMillis() - startTime
    Log.i(TAG, "Account switch to $targetAccountId completed in ${elapsed}ms")

    onComplete?.invoke()
  }

  /**
   * Creates a new account directory and registers it in the account registry.
   * Does NOT switch to the new account -- call [switchToAccount] for that.
   *
   * If the current account hasn't been migrated into the accounts/ directory
   * structure yet (e.g., fresh install where the app hasn't restarted since
   * initial registration), this method will migrate it first so the existing
   * data isn't orphaned.
   *
   * @return The account ID of the newly created account
   */
  @JvmStatic
  fun addAccount(application: Application): String {
    val registry = AccountRegistry.getInstance(application)

    if (!registry.hasAccounts() && SignalDatabase.databaseFileExists(application)) {
      Log.i(TAG, "Current account not yet in registry -- migrating before adding new account")

      try {
        SignalStore.blockUntilAllWritesFinished()
      } catch (e: Exception) {
        Log.w(TAG, "Error flushing writes before migration", e)
      }

      val currentAccountId = AccountFileManager.migrateExistingDataToAccountDir(application)
      registry.addAccount(setActive = true)

      reinitDatabases(application, currentAccountId)
      syncRegistryWithActiveAccount(application)
    }

    val accountId = registry.addAccount(setActive = false)
    AccountFileManager.createAccountDir(application, accountId)
    Log.i(TAG, "Created new account: $accountId")
    return accountId
  }

  /**
   * Removes an account, deleting its data and registry entry.
   * Cannot remove the currently active account.
   */
  @JvmStatic
  fun removeAccount(application: Application, accountId: String) {
    val registry = AccountRegistry.getInstance(application)
    val active = registry.getActiveAccount()
    require(active?.accountId != accountId) { "Cannot remove the currently active account" }

    AccountFileManager.deleteAccountDir(application, accountId)
    registry.removeAccount(accountId)
    Log.i(TAG, "Removed account: $accountId")
  }

  /**
   * Returns the list of all registered accounts.
   */
  @JvmStatic
  fun getAccounts(application: Application): List<AccountRegistry.AccountEntry> {
    return AccountRegistry.getInstance(application).getAllAccounts()
  }

  /**
   * Returns the currently active account, or null if none.
   */
  @JvmStatic
  fun getActiveAccount(application: Application): AccountRegistry.AccountEntry? {
    return AccountRegistry.getInstance(application).getActiveAccount()
  }

  /**
   * Returns the number of registered accounts.
   */
  @JvmStatic
  fun getAccountCount(application: Application): Int {
    return AccountRegistry.getInstance(application).getAccountCount()
  }

  private fun reinitDatabases(application: Application, accountId: String) {
    val databaseSecret = DatabaseSecretProvider.getOrCreateDatabaseSecret(application)
    val attachmentSecret = AttachmentSecretProvider.getInstance(application).getOrCreateAttachmentSecret()

    val signalDbPath = AccountFileManager.getAccountDatabasePath(application, accountId, "signal.db")
    val kvDbPath = AccountFileManager.getAccountDatabasePath(application, accountId, "signal-key-value.db")
    val jobDbPath = AccountFileManager.getAccountDatabasePath(application, accountId, "signal-jobmanager.db")

    // Reinit in dependency order: KV store first (SignalStore depends on it),
    // then main database, then job database
    KeyValueDatabase.reinit(application, kvDbPath)
    SignalStore.reinit(application)
    SignalDatabase.reinit(application, databaseSecret, attachmentSecret, signalDbPath)
    JobDatabase.reinit(application, jobDbPath)
  }

  /**
   * One-time migration: moves existing single-account data into the accounts/ directory
   * structure and creates the initial account registry entry.
   *
   * Should be called during ApplicationContext.onCreate() on first launch after
   * multi-account support is added.
   */
  @JvmStatic
  fun migrateToMultiAccountIfNeeded(application: Application) {
    val registry = AccountRegistry.getInstance(application)

    if (registry.hasAccounts()) {
      return
    }

    // Check if there's existing data to migrate
    if (SignalDatabase.databaseFileExists(application)) {
      Log.i(TAG, "Migrating existing single-account data to multi-account structure")

      // Close current databases before moving files
      // Note: This runs before singletons are initialized in the new flow,
      // so we just need to move the files.
      val accountId = AccountFileManager.migrateExistingDataToAccountDir(application)
      registry.addAccount(setActive = true)
      registry.updateAccountIdentity(
        accountId = accountId,
        aci = null, // Will be populated from SignalStore once it reinits
        e164 = null,
        displayName = null
      )
      Log.i(TAG, "Migration complete. Active account: $accountId")
    } else {
      // No existing data -- this is a fresh install.
      // The first account will be created during registration.
      Log.i(TAG, "No existing data to migrate. Fresh install.")
    }
  }

  /**
   * Called after the SignalStore is initialized for the active account, to sync
   * the account registry with the actual identity values.
   */
  @JvmStatic
  fun syncRegistryWithActiveAccount(application: Application) {
    val registry = AccountRegistry.getInstance(application)
    val active = registry.getActiveAccount() ?: return

    try {
      val aci = SignalStore.account.aci?.toString()
      val e164 = SignalStore.account.e164

      val displayName = try {
        val profileName = Recipient.self().profileName.toString()
        if (profileName.isNotBlank()) profileName else e164
      } catch (e: Exception) {
        e164
      }

      if (aci != null || e164 != null) {
        registry.updateAccountIdentity(
          accountId = active.accountId,
          aci = aci,
          e164 = e164,
          displayName = displayName
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not sync registry with active account", e)
    }
  }
}

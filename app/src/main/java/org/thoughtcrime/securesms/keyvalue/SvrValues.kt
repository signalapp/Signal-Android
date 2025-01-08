package org.thoughtcrime.securesms.keyvalue

import org.signal.core.util.StringStringSerializer
import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil.localPinHash

class SvrValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    private val TAG = Log.tag(SvrValues::class)

    const val REGISTRATION_LOCK_ENABLED: String = "kbs.v2_lock_enabled"
    const val OPTED_OUT: String = "kbs.opted_out"

    private const val PIN = "kbs.pin"
    private const val LOCK_LOCAL_PIN_HASH = "kbs.registration_lock_local_pin_hash"
    private const val LAST_CREATE_FAILED_TIMESTAMP = "kbs.last_create_failed_timestamp"
    private const val PIN_FORGOTTEN_OR_SKIPPED = "kbs.pin.forgotten.or.skipped"
    private const val SVR2_AUTH_TOKENS = "kbs.kbs_auth_tokens"
    private const val SVR_LAST_AUTH_REFRESH_TIMESTAMP = "kbs.kbs_auth_tokens.last_refresh_timestamp"
    private const val SVR3_AUTH_TOKENS = "kbs.svr3_auth_tokens"
    private const val RESTORED_VIA_ACCOUNT_ENTROPY_KEY = "kbs.restore_via_account_entropy_pool"
    private const val INITIAL_RESTORE_MASTER_KEY = "kbs.initialRestoreMasterKey"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      SVR2_AUTH_TOKENS,
      SVR3_AUTH_TOKENS
    )
  }

  /** Deliberately does not clear the [MASTER_KEY]. */
  @Synchronized
  fun clearRegistrationLockAndPin() {
    store.beginWrite()
      .remove(REGISTRATION_LOCK_ENABLED)
      .remove(LOCK_LOCAL_PIN_HASH)
      .remove(PIN)
      .remove(LAST_CREATE_FAILED_TIMESTAMP)
      .remove(OPTED_OUT)
      .remove(SVR2_AUTH_TOKENS)
      .remove(SVR_LAST_AUTH_REFRESH_TIMESTAMP)
      .commit()
  }

  @Synchronized
  fun setPin(pin: String) {
    store.beginWrite()
      .putString(PIN, pin)
      .putString(LOCK_LOCAL_PIN_HASH, localPinHash(pin))
      .putBoolean(OPTED_OUT, false)
      .commit()
  }

  @Synchronized
  fun setPinIfNotPresent(pin: String) {
    if (store.getString(PIN, null) == null) {
      store.beginWrite()
        .putString(PIN, pin)
        .putString(LOCK_LOCAL_PIN_HASH, localPinHash(pin))
        .putBoolean(OPTED_OUT, false)
        .commit()
    }
  }

  /** Whether or not registration lock V2 is enabled. */
  @get:Synchronized
  @set:Synchronized
  var isRegistrationLockEnabled: Boolean by booleanValue(REGISTRATION_LOCK_ENABLED, false)

  @Synchronized
  fun onPinCreateFailure() {
    putLong(LAST_CREATE_FAILED_TIMESTAMP, System.currentTimeMillis())
  }

  /** Whether or not the last time the user attempted to create a PIN, it failed. */
  @Synchronized
  fun lastPinCreateFailed(): Boolean {
    return getLong(LAST_CREATE_FAILED_TIMESTAMP, -1) > 0
  }

  /** Returns the Master Key */
  val masterKey: MasterKey
    get() = SignalStore.account.accountEntropyPool.deriveMasterKey()

  /**
   * The [MasterKey] that should be used for our initial syncs with storage service + recovery password.
   * The presence of this value indicates that it hasn't been used yet for storage service.
   * Once there has been *any* write to storage service, this value needs to be cleared.
   */
  @get:Synchronized
  @set:Synchronized
  var masterKeyForInitialDataRestore: MasterKey?
    get() {
      return getBlob(INITIAL_RESTORE_MASTER_KEY, null)?.let { MasterKey(it) }
    }
    set(value) {
      if (value != masterKeyForInitialDataRestore) {
        if (value == masterKey) {
          Log.w(TAG, "The master key already matches the one derived from the AEP! All good, no need to store it.")
          store.beginWrite().putBlob(INITIAL_RESTORE_MASTER_KEY, null).commit()
        } else if (value != null) {
          Log.w(TAG, "Setting initial restore master key!", Throwable())
          store.beginWrite().putBlob(INITIAL_RESTORE_MASTER_KEY, value.serialize()).commit()
        } else {
          Log.w(TAG, "Clearing initial restore master key!", Throwable())
          store.beginWrite().putBlob(INITIAL_RESTORE_MASTER_KEY, null).commit()
        }
      }
    }

  @get:Synchronized
  val pinBackedMasterKey: MasterKey?
    /** Returns null if master key is not backed up by a pin. */
    get() {
      if (!isRegistrationLockEnabled) return null
      return masterKey
    }

  @get:Synchronized
  val registrationLockToken: String?
    get() {
      val masterKey = pinBackedMasterKey
      return masterKey?.deriveRegistrationLock()
    }

  @get:Synchronized
  val recoveryPassword: String?
    get() {
      return if (hasOptedInWithAccess()) {
        masterKeyForInitialDataRestore?.deriveRegistrationRecoveryPassword() ?: masterKey.deriveRegistrationRecoveryPassword()
      } else {
        null
      }
    }

  @get:Synchronized
  val pin: String? by stringValue(PIN, null)

  @get:Synchronized
  val localPinHash: String? by stringValue(LOCK_LOCAL_PIN_HASH, null)

  @Synchronized
  fun hasOptedInWithAccess(): Boolean {
    return hasPin() || restoredViaAccountEntropyPool
  }

  @Synchronized
  fun hasPin(): Boolean {
    return localPinHash != null
  }

  @get:Synchronized
  val restoredViaAccountEntropyPool by booleanValue(RESTORED_VIA_ACCOUNT_ENTROPY_KEY, false)

  @get:Synchronized
  @set:Synchronized
  var isPinForgottenOrSkipped: Boolean by booleanValue(PIN_FORGOTTEN_OR_SKIPPED, false)

  @Synchronized
  fun putSvr2AuthTokens(tokens: List<String>) {
    putList(SVR2_AUTH_TOKENS, tokens, StringStringSerializer)
    lastRefreshAuthTimestamp = System.currentTimeMillis()
  }

  @Synchronized
  fun putSvr3AuthTokens(tokens: List<String>) {
    putList(SVR3_AUTH_TOKENS, tokens, StringStringSerializer)
    lastRefreshAuthTimestamp = System.currentTimeMillis()
  }

  @get:Synchronized
  val svr2AuthTokens: List<String>
    get() = getList(SVR2_AUTH_TOKENS, StringStringSerializer).requireNoNulls()

  @get:Synchronized
  val svr3AuthTokens: List<String>
    get() = getList(SVR3_AUTH_TOKENS, StringStringSerializer).requireNoNulls()

  /**
   * Keeps the 10 most recent KBS auth tokens.
   * @param token
   * @return whether the token was added (new) or ignored (already existed)
   */
  @Synchronized
  fun appendSvr2AuthTokenToList(token: String): Boolean {
    val tokens = svr2AuthTokens
    if (tokens.contains(token)) {
      return false
    } else {
      val result = (listOf(token) + tokens).take(10)
      putSvr2AuthTokens(result)
      return true
    }
  }

  /**
   * Keeps the 10 most recent SVR3 auth tokens.
   * @param token
   * @return whether the token was added (new) or ignored (already existed)
   */
  @Synchronized
  fun appendSvr3AuthTokenToList(token: String): Boolean {
    val tokens = svr3AuthTokens
    if (tokens.contains(token)) {
      return false
    } else {
      val result = (listOf(token) + tokens).take(10)
      putSvr3AuthTokens(result)
      return true
    }
  }

  @Synchronized
  fun removeSvr2AuthTokens(invalid: List<String>): Boolean {
    val tokens: MutableList<String> = ArrayList(svr2AuthTokens)
    if (tokens.removeAll(invalid)) {
      putSvr2AuthTokens(tokens)
      return true
    }

    return false
  }

  @Synchronized
  fun removeSvr3AuthTokens(invalid: List<String>): Boolean {
    val tokens: MutableList<String> = ArrayList(svr3AuthTokens)
    if (tokens.removeAll(invalid)) {
      putSvr3AuthTokens(tokens)
      return true
    }

    return false
  }

  @Synchronized
  fun optOut() {
    store.beginWrite()
      .putBoolean(OPTED_OUT, true)
      .remove(LOCK_LOCAL_PIN_HASH)
      .remove(PIN)
      .remove(RESTORED_VIA_ACCOUNT_ENTROPY_KEY)
      .putLong(LAST_CREATE_FAILED_TIMESTAMP, -1)
      .commit()
  }

  @Synchronized
  fun hasOptedOut(): Boolean {
    return getBoolean(OPTED_OUT, false)
  }

  var lastRefreshAuthTimestamp: Long by longValue(SVR_LAST_AUTH_REFRESH_TIMESTAMP, 0L)
}

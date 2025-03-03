package org.thoughtcrime.securesms.keyvalue

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MasterCipher
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.AccountEntropyPool
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.ServiceIds
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.signal.libsignal.messagebackup.AccountEntropyPool as LibSignalAccountEntropyPool

class AccountValues internal constructor(store: KeyValueStore, context: Context) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(AccountValues::class.java)
    private const val KEY_SERVICE_PASSWORD = "account.service_password"
    private const val KEY_REGISTRATION_ID = "account.registration_id"
    private const val KEY_FCM_ENABLED = "account.fcm_enabled"
    private const val KEY_FCM_TOKEN = "account.fcm_token"
    private const val KEY_FCM_TOKEN_VERSION = "account.fcm_token_version"
    private const val KEY_FCM_TOKEN_LAST_SET_TIME = "account.fcm_token_last_set_time"
    private const val KEY_DEVICE_NAME = "account.device_name"
    private const val KEY_DEVICE_ID = "account.device_id"
    private const val KEY_PNI_REGISTRATION_ID = "account.pni_registration_id"

    private const val KEY_ACI_IDENTITY_PUBLIC_KEY = "account.aci_identity_public_key"
    private const val KEY_ACI_IDENTITY_PRIVATE_KEY = "account.aci_identity_private_key"
    private const val KEY_ACI_SIGNED_PREKEY_REGISTERED = "account.aci_signed_prekey_registered"
    private const val KEY_ACI_NEXT_SIGNED_PREKEY_ID = "account.aci_next_signed_prekey_id"
    private const val KEY_ACI_ACTIVE_SIGNED_PREKEY_ID = "account.aci_active_signed_prekey_id"
    private const val KEY_ACI_LAST_SIGNED_PREKEY_ROTATION_TIME = "account.aci_last_signed_prekey_rotation_time"
    private const val KEY_ACI_NEXT_ONE_TIME_PREKEY_ID = "account.aci_next_one_time_prekey_id"
    private const val KEY_ACI_NEXT_KYBER_PREKEY_ID = "account.aci_next_kyber_prekey_id"
    private const val KEY_ACI_LAST_RESORT_KYBER_PREKEY_ID = "account.aci_last_resort_kyber_prekey_id"
    private const val KEY_ACI_LAST_RESORT_KYBER_PREKEY_ROTATION_TIME = "account.aci_last_resort_kyber_prekey_rotation_time"

    private const val KEY_PNI_IDENTITY_PUBLIC_KEY = "account.pni_identity_public_key"
    private const val KEY_PNI_IDENTITY_PRIVATE_KEY = "account.pni_identity_private_key"
    private const val KEY_PNI_SIGNED_PREKEY_REGISTERED = "account.pni_signed_prekey_registered"
    private const val KEY_PNI_NEXT_SIGNED_PREKEY_ID = "account.pni_next_signed_prekey_id"
    private const val KEY_PNI_ACTIVE_SIGNED_PREKEY_ID = "account.pni_active_signed_prekey_id"
    private const val KEY_PNI_LAST_SIGNED_PREKEY_ROTATION_TIME = "account.pni_last_signed_prekey_rotation_time"
    private const val KEY_PNI_NEXT_ONE_TIME_PREKEY_ID = "account.pni_next_one_time_prekey_id"
    private const val KEY_PNI_NEXT_KYBER_PREKEY_ID = "account.pni_next_kyber_prekey_id"
    private const val KEY_PNI_LAST_RESORT_KYBER_PREKEY_ID = "account.pni_last_resort_kyber_prekey_id"
    private const val KEY_PNI_LAST_RESORT_KYBER_PREKEY_ROTATION_TIME = "account.pni_last_resort_kyber_prekey_rotation_time"

    private const val KEY_USERNAME = "account.username"
    private const val KEY_USERNAME_LINK_ENTROPY = "account.username_link_entropy"
    private const val KEY_USERNAME_LINK_SERVER_ID = "account.username_link_server_id"
    private const val KEY_USERNAME_SYNC_STATE = "phoneNumberPrivacy.usernameSyncState"
    private const val KEY_USERNAME_SYNC_ERROR_COUNT = "phoneNumberPrivacy.usernameErrorCount"

    private const val KEY_E164 = "account.e164"
    private const val KEY_ACI = "account.aci"
    private const val KEY_PNI = "account.pni"
    private const val KEY_IS_REGISTERED = "account.is_registered"
    private const val KEY_ACCOUNT_REGISTERED_AT = "account.registered_at"

    private const val KEY_HAS_LINKED_DEVICES = "account.has_linked_devices"

    private const val KEY_ACCOUNT_ENTROPY_POOL = "account.account_entropy_pool"
    private const val KEY_RESTORED_ACCOUNT_ENTROPY_KEY = "account.restored_account_entropy_pool"

    private val AEP_LOCK = ReentrantLock()
  }

  init {
    if (!store.containsKey(KEY_ACI)) {
      migrateFromSharedPrefsV1(context)
    }

    if (!store.containsKey(KEY_ACI_IDENTITY_PUBLIC_KEY)) {
      migrateFromSharedPrefsV2(context)
    }

    if (!store.containsKey(KEY_HAS_LINKED_DEVICES)) {
      migrateFromSharedPrefsV3(context)
    }

    store.getString(KEY_PNI, null)?.let { pni ->
      if (!pni.startsWith("PNI:")) {
        store.beginWrite().putString(KEY_PNI, "PNI:$pni").commit()
      }
    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      KEY_ACI_IDENTITY_PUBLIC_KEY,
      KEY_ACI_IDENTITY_PRIVATE_KEY,
      KEY_PNI_IDENTITY_PUBLIC_KEY,
      KEY_PNI_IDENTITY_PRIVATE_KEY,
      KEY_USERNAME,
      KEY_USERNAME_LINK_ENTROPY,
      KEY_USERNAME_LINK_SERVER_ID,
      KEY_ACCOUNT_ENTROPY_POOL
    )
  }

  val accountEntropyPool: AccountEntropyPool
    get() {
      AEP_LOCK.withLock {
        getString(KEY_ACCOUNT_ENTROPY_POOL, null)?.let {
          return AccountEntropyPool(it)
        }

        Log.i(TAG, "Generating Account Entropy Pool (AEP)...")
        val newAep = LibSignalAccountEntropyPool.generate()
        putString(KEY_ACCOUNT_ENTROPY_POOL, newAep)
        return AccountEntropyPool(newAep)
      }
    }

  fun restoreAccountEntropyPool(aep: AccountEntropyPool) {
    AEP_LOCK.withLock {
      store
        .beginWrite()
        .putString(KEY_ACCOUNT_ENTROPY_POOL, aep.value)
        .putBoolean(KEY_RESTORED_ACCOUNT_ENTROPY_KEY, true)
        .commit()
    }
  }

  fun resetAccountEntropyPool() {
    AEP_LOCK.withLock {
      Log.i(TAG, "Resetting Account Entropy Pool (AEP)", Throwable())
      store
        .beginWrite()
        .putString(KEY_ACCOUNT_ENTROPY_POOL, null)
        .putBoolean(KEY_RESTORED_ACCOUNT_ENTROPY_KEY, false)
        .commit()
    }
  }

  @get:Synchronized
  val restoredAccountEntropyPool by booleanValue(KEY_RESTORED_ACCOUNT_ENTROPY_KEY, false)

  /** The local user's [ACI]. */
  val aci: ACI?
    get() = ACI.parseOrNull(getString(KEY_ACI, null))

  /** The local user's [ACI]. Will throw if not present. */
  fun requireAci(): ACI {
    return ACI.parseOrThrow(getString(KEY_ACI, null))
  }

  fun setAci(aci: ACI) {
    putString(KEY_ACI, aci.toString())
  }

  /** The local user's [PNI]. */
  val pni: PNI?
    get() = PNI.parseOrNull(getString(KEY_PNI, null))

  /** The local user's [PNI]. Will throw if not present. */
  fun requirePni(): PNI {
    return PNI.parseOrThrow(getString(KEY_PNI, null))
  }

  fun setPni(pni: PNI) {
    putString(KEY_PNI, pni.toString())
  }

  fun getServiceIds(): ServiceIds {
    return ServiceIds(requireAci(), pni)
  }

  /** The local user's E164. */
  val e164: String?
    get() = getString(KEY_E164, null)

  /** The local user's e164. Will throw if not present. */
  fun requireE164(): String {
    val e164: String? = getString(KEY_E164, null)
    return e164 ?: throw IllegalStateException("No e164!")
  }

  fun setE164(e164: String) {
    putString(KEY_E164, e164)
  }

  /** The password for communicating with the Signal service. */
  val servicePassword: String?
    get() = getString(KEY_SERVICE_PASSWORD, null)

  fun setServicePassword(servicePassword: String) {
    putString(KEY_SERVICE_PASSWORD, servicePassword)
  }

  /** A randomly-generated value that represents this registration instance. Helps the server know if you reinstalled. */
  var registrationId: Int by integerValue(KEY_REGISTRATION_ID, 0)

  var pniRegistrationId: Int by integerValue(KEY_PNI_REGISTRATION_ID, 0)

  /** The identity key pair for the ACI identity. */
  val aciIdentityKey: IdentityKeyPair
    get() {
      require(store.containsKey(KEY_ACI_IDENTITY_PUBLIC_KEY)) { "Not yet set!" }
      return IdentityKeyPair(
        IdentityKey(getBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, null)),
        Curve.decodePrivatePoint(getBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, null))
      )
    }

  /** The identity key pair for the PNI identity. */
  val pniIdentityKey: IdentityKeyPair
    get() {
      require(store.containsKey(KEY_PNI_IDENTITY_PUBLIC_KEY)) { "Not yet set!" }
      return IdentityKeyPair(
        IdentityKey(getBlob(KEY_PNI_IDENTITY_PUBLIC_KEY, null)),
        Curve.decodePrivatePoint(getBlob(KEY_PNI_IDENTITY_PRIVATE_KEY, null))
      )
    }

  fun hasAciIdentityKey(): Boolean {
    return store.containsKey(KEY_ACI_IDENTITY_PUBLIC_KEY)
  }

  /** Generates and saves an identity key pair for the ACI identity. Should only be done once. */
  fun generateAciIdentityKeyIfNecessary() {
    synchronized(this) {
      if (store.containsKey(KEY_ACI_IDENTITY_PUBLIC_KEY)) {
        Log.w(TAG, "Tried to generate an ACI identity, but one was already set!", Throwable())
        return
      }

      Log.i(TAG, "Generating a new ACI identity key pair.")

      val key: IdentityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
      store
        .beginWrite()
        .putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, key.publicKey.serialize())
        .putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, key.privateKey.serialize())
        .commit()
    }
  }

  fun hasPniIdentityKey(): Boolean {
    return store.containsKey(KEY_PNI_IDENTITY_PUBLIC_KEY)
  }

  /** Generates and saves an identity key pair for the PNI identity if one doesn't already exist. */
  fun generatePniIdentityKeyIfNecessary() {
    synchronized(this) {
      if (store.containsKey(KEY_PNI_IDENTITY_PUBLIC_KEY)) {
        Log.w(TAG, "Tried to generate a PNI identity, but one was already set!", Throwable())
        return
      }

      Log.i(TAG, "Generating a new PNI identity key pair.")

      val key: IdentityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
      store
        .beginWrite()
        .putBlob(KEY_PNI_IDENTITY_PUBLIC_KEY, key.publicKey.serialize())
        .putBlob(KEY_PNI_IDENTITY_PRIVATE_KEY, key.privateKey.serialize())
        .commit()
    }
  }

  /** When acting as a linked device, this method lets you store the identity keys sent from the primary device */
  fun setAciIdentityKeysFromPrimaryDevice(aciKeys: IdentityKeyPair) {
    synchronized(this) {
      require(isLinkedDevice) { "Must be a linked device!" }
      store
        .beginWrite()
        .putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, aciKeys.publicKey.serialize())
        .putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, aciKeys.privateKey.serialize())
        .commit()
    }
  }

  /** Set an identity key pair for the PNI identity via change number. */
  fun setPniIdentityKeyAfterChangeNumber(key: IdentityKeyPair) {
    synchronized(this) {
      Log.i(TAG, "Setting a new PNI identity key pair.")

      store
        .beginWrite()
        .putBlob(KEY_PNI_IDENTITY_PUBLIC_KEY, key.publicKey.serialize())
        .putBlob(KEY_PNI_IDENTITY_PRIVATE_KEY, key.privateKey.serialize())
        .commit()
    }
  }

  fun restorePniIdentityKeyFromBackup(publicKey: ByteArray, privateKey: ByteArray) {
    synchronized(this) {
      Log.i(TAG, "Setting a new PNI identity key pair.")

      store
        .beginWrite()
        .putBlob(KEY_PNI_IDENTITY_PUBLIC_KEY, publicKey)
        .putBlob(KEY_PNI_IDENTITY_PRIVATE_KEY, privateKey)
        .commit()
    }
  }

  fun restoreAciIdentityKeyFromBackup(publicKey: ByteArray, privateKey: ByteArray) {
    synchronized(this) {
      Log.i(TAG, "Setting a new ACI identity key pair.")

      store
        .beginWrite()
        .putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, publicKey)
        .putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, privateKey)
        .commit()
    }
  }

  /** Only to be used when restoring an identity public key from an old backup */
  fun restoreLegacyIdentityPublicKeyFromBackup(base64: String) {
    Log.w(TAG, "Restoring legacy identity public key from backup.")
    putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, Base64.decode(base64))
  }

  /** Only to be used when restoring an identity private key from an old backup */
  fun restoreLegacyIdentityPrivateKeyFromBackup(base64: String) {
    Log.w(TAG, "Restoring legacy identity private key from backup.")
    putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, Base64.decode(base64))
  }

  @get:JvmName("aciPreKeys")
  val aciPreKeys: PreKeyMetadataStore = object : PreKeyMetadataStore {
    override var nextSignedPreKeyId: Int by integerValue(KEY_ACI_NEXT_SIGNED_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var activeSignedPreKeyId: Int by integerValue(KEY_ACI_ACTIVE_SIGNED_PREKEY_ID, -1)
    override var isSignedPreKeyRegistered: Boolean by booleanValue(KEY_ACI_SIGNED_PREKEY_REGISTERED, false)
    override var lastSignedPreKeyRotationTime: Long by longValue(KEY_ACI_LAST_SIGNED_PREKEY_ROTATION_TIME, System.currentTimeMillis() - PreKeysSyncJob.REFRESH_INTERVAL)
    override var nextEcOneTimePreKeyId: Int by integerValue(KEY_ACI_NEXT_ONE_TIME_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var nextKyberPreKeyId: Int by integerValue(KEY_ACI_NEXT_KYBER_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var lastResortKyberPreKeyId: Int by integerValue(KEY_ACI_LAST_RESORT_KYBER_PREKEY_ID, -1)
    override var lastResortKyberPreKeyRotationTime: Long by longValue(KEY_ACI_LAST_RESORT_KYBER_PREKEY_ROTATION_TIME, 0)
  }

  @get:JvmName("pniPreKeys")
  val pniPreKeys: PreKeyMetadataStore = object : PreKeyMetadataStore {
    override var nextSignedPreKeyId: Int by integerValue(KEY_PNI_NEXT_SIGNED_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var activeSignedPreKeyId: Int by integerValue(KEY_PNI_ACTIVE_SIGNED_PREKEY_ID, -1)
    override var isSignedPreKeyRegistered: Boolean by booleanValue(KEY_PNI_SIGNED_PREKEY_REGISTERED, false)
    override var lastSignedPreKeyRotationTime: Long by longValue(KEY_PNI_LAST_SIGNED_PREKEY_ROTATION_TIME, System.currentTimeMillis() - PreKeysSyncJob.REFRESH_INTERVAL)
    override var nextEcOneTimePreKeyId: Int by integerValue(KEY_PNI_NEXT_ONE_TIME_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var nextKyberPreKeyId: Int by integerValue(KEY_PNI_NEXT_KYBER_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var lastResortKyberPreKeyId: Int by integerValue(KEY_PNI_LAST_RESORT_KYBER_PREKEY_ID, -1)
    override var lastResortKyberPreKeyRotationTime: Long by longValue(KEY_PNI_LAST_RESORT_KYBER_PREKEY_ROTATION_TIME, 0)
  }

  /** Indicates whether the user has the ability to receive FCM messages. Largely coupled to whether they have Play Service. */
  @get:JvmName("isFcmEnabled")
  var fcmEnabled: Boolean by booleanValue(KEY_FCM_ENABLED, false)

  /** The FCM token, which allows the server to send us FCM messages. */
  var fcmToken: String?
    get() {
      val tokenVersion: Int = getInteger(KEY_FCM_TOKEN_VERSION, 0)
      return if (tokenVersion == Util.getCanonicalVersionCode()) {
        getString(KEY_FCM_TOKEN, null)
      } else {
        null
      }
    }
    set(value) {
      store.beginWrite()
        .putString(KEY_FCM_TOKEN, value)
        .putInteger(KEY_FCM_TOKEN_VERSION, Util.getCanonicalVersionCode())
        .putLong(KEY_FCM_TOKEN_LAST_SET_TIME, System.currentTimeMillis())
        .apply()
    }

  /** When we last set the [fcmToken] */
  val fcmTokenLastSetTime: Long
    get() = getLong(KEY_FCM_TOKEN_LAST_SET_TIME, 0)

  /** Whether or not the user is registered with the Signal service. */
  val isRegistered: Boolean
    get() = getBoolean(KEY_IS_REGISTERED, false)

  fun setRegistered(registered: Boolean) {
    Log.i(TAG, "Setting push registered: $registered", Throwable())

    val previous = isRegistered

    putBoolean(KEY_IS_REGISTERED, registered)

    AppDependencies.incomingMessageObserver.notifyRegistrationStateChanged()

    if (previous != registered) {
      Recipient.self().live().refresh()
    }

    if (previous && !registered) {
      clearLocalCredentials()
    }

    if (!previous && registered) {
      registeredAtTimestamp = System.currentTimeMillis()
    } else if (!registered) {
      registeredAtTimestamp = -1
    }
  }

  /**
   * Milliseconds since epoch when account was registered or a negative value if not known.
   */
  var registeredAtTimestamp: Long by longValue(KEY_ACCOUNT_REGISTERED_AT, -1)
    private set

  /**
   * Function for testing backup/restore
   */
  @Deprecated("debug only")
  fun clearRegistrationButKeepCredentials() {
    putBoolean(KEY_IS_REGISTERED, false)

    AppDependencies.incomingMessageObserver.notifyRegistrationStateChanged()

    Recipient.self().live().refresh()
  }

  val deviceName: String?
    get() = getString(KEY_DEVICE_NAME, null)

  fun setDeviceName(deviceName: String) {
    putString(KEY_DEVICE_NAME, deviceName)
  }

  var deviceId: Int by integerValue(KEY_DEVICE_ID, SignalServiceAddress.DEFAULT_DEVICE_ID)

  val isPrimaryDevice: Boolean
    get() = deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID

  val isLinkedDevice: Boolean
    get() = !isPrimaryDevice

  /** The local user's full username (nickname.discriminator), if set. */
  var username: String?
    get() {
      val value = getString(KEY_USERNAME, null)
      return value.nullIfBlank()
    }
    set(value) {
      putString(KEY_USERNAME, value.nullIfBlank())
    }

  /** The local user's username link components, if set. */
  var usernameLink: UsernameLinkComponents?
    get() {
      val entropy: ByteArray? = getBlob(KEY_USERNAME_LINK_ENTROPY, null)
      val serverId: ByteArray? = getBlob(KEY_USERNAME_LINK_SERVER_ID, null)

      return if (entropy != null && serverId != null) {
        val serverIdUuid = UuidUtil.parseOrThrow(serverId)
        UsernameLinkComponents(entropy, serverIdUuid)
      } else {
        null
      }
    }
    set(value) {
      store
        .beginWrite()
        .putBlob(KEY_USERNAME_LINK_ENTROPY, value?.entropy)
        .putBlob(KEY_USERNAME_LINK_SERVER_ID, value?.serverId?.toByteArray())
        .apply()
    }

  /**
   * There are some cases where our username may fall out of sync with the service. In particular, we may get a new value for our username from
   * storage service but then find that it doesn't match what's on the service.
   */
  var usernameSyncState: UsernameSyncState
    get() = UsernameSyncState.deserialize(getLong(KEY_USERNAME_SYNC_STATE, UsernameSyncState.IN_SYNC.serialize()))
    set(value) {
      Log.i(TAG, "Marking username sync state as: $value")
      putLong(KEY_USERNAME_SYNC_STATE, value.serialize())
    }

  var usernameSyncErrorCount: Int by integerValue(KEY_USERNAME_SYNC_ERROR_COUNT, 0)

  private fun clearLocalCredentials() {
    putString(KEY_SERVICE_PASSWORD, Util.getSecret(18))

    val newProfileKey = ProfileKeyUtil.createNew()
    val self = Recipient.self()

    SignalDatabase.recipients.setProfileKey(self.id, newProfileKey)
    AppDependencies.groupsV2Authorization.clear()
  }

  /**
   * Whether or not the user has linked devices.
   */
  @get:JvmName("hasLinkedDevices")
  var hasLinkedDevices by booleanValue(KEY_HAS_LINKED_DEVICES, false)

  /** Do not alter. If you need to migrate more stuff, create a new method. */
  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating account values from shared prefs.")

    putString(KEY_ACI, TextSecurePreferences.getStringPreference(context, "pref_local_uuid", null))
    putString(KEY_E164, TextSecurePreferences.getStringPreference(context, "pref_local_number", null))
    putString(KEY_SERVICE_PASSWORD, TextSecurePreferences.getStringPreference(context, "pref_gcm_password", null))
    putBoolean(KEY_IS_REGISTERED, TextSecurePreferences.getBooleanPreference(context, "pref_gcm_registered", false))
    putInteger(KEY_REGISTRATION_ID, TextSecurePreferences.getIntegerPreference(context, "pref_local_registration_id", 0))
    putBoolean(KEY_FCM_ENABLED, !TextSecurePreferences.getBooleanPreference(context, "pref_gcm_disabled", false))
    putString(KEY_FCM_TOKEN, TextSecurePreferences.getStringPreference(context, "pref_gcm_registration_id", null))
    putInteger(KEY_FCM_TOKEN_VERSION, TextSecurePreferences.getIntegerPreference(context, "pref_gcm_registration_id_version", 0))
    putLong(KEY_FCM_TOKEN_LAST_SET_TIME, TextSecurePreferences.getLongPreference(context, "pref_gcm_registration_id_last_set_time", 0))
  }

  /** Do not alter. If you need to migrate more stuff, create a new method. */
  @SuppressLint("ApplySharedPref")
  @Suppress("DEPRECATION")
  private fun migrateFromSharedPrefsV2(context: Context) {
    Log.i(TAG, "[V2] Migrating account values from shared prefs.")

    val masterSecretPrefs: SharedPreferences = context.getSharedPreferences("SecureSMS-Preferences", 0)
    val defaultPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val storeWriter: KeyValueStore.Writer = store.beginWrite()

    if (masterSecretPrefs.hasStringData("pref_identity_public_v3")) {
      Log.i(TAG, "Migrating modern identity key.")

      val identityPublic = Base64.decode(masterSecretPrefs.getString("pref_identity_public_v3", null)!!)
      val identityPrivate = Base64.decode(masterSecretPrefs.getString("pref_identity_private_v3", null)!!)

      storeWriter
        .putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, identityPublic)
        .putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, identityPrivate)
    } else if (masterSecretPrefs.hasStringData("pref_identity_public_curve25519")) {
      Log.i(TAG, "Migrating legacy identity key.")

      val masterCipher = MasterCipher(KeyCachingService.getMasterSecret(context))
      val identityPublic = Base64.decode(masterSecretPrefs.getString("pref_identity_public_curve25519", null)!!)
      val identityPrivate = masterCipher.decryptKey(Base64.decode(masterSecretPrefs.getString("pref_identity_private_curve25519", null)!!)).serialize()

      storeWriter
        .putBlob(KEY_ACI_IDENTITY_PUBLIC_KEY, identityPublic)
        .putBlob(KEY_ACI_IDENTITY_PRIVATE_KEY, identityPrivate)
    } else {
      Log.w(TAG, "No pre-existing identity key! No migration.")
    }

    storeWriter
      .putInteger(KEY_ACI_NEXT_SIGNED_PREKEY_ID, defaultPrefs.getInt("pref_next_signed_pre_key_id", SecureRandom().nextInt(Medium.MAX_VALUE)))
      .putInteger(KEY_ACI_ACTIVE_SIGNED_PREKEY_ID, defaultPrefs.getInt("pref_active_signed_pre_key_id", -1))
      .putInteger(KEY_ACI_NEXT_ONE_TIME_PREKEY_ID, defaultPrefs.getInt("pref_next_pre_key_id", SecureRandom().nextInt(Medium.MAX_VALUE)))
      .putBoolean(KEY_ACI_SIGNED_PREKEY_REGISTERED, defaultPrefs.getBoolean("pref_signed_prekey_registered", false))
      .commit()

    masterSecretPrefs
      .edit()
      .remove("pref_identity_public_v3")
      .remove("pref_identity_private_v3")
      .remove("pref_identity_public_curve25519")
      .remove("pref_identity_private_curve25519")
      .commit()

    defaultPrefs
      .edit()
      .remove("pref_local_uuid")
      .remove("pref_identity_public_v3")
      .remove("pref_next_signed_pre_key_id")
      .remove("pref_active_signed_pre_key_id")
      .remove("pref_signed_prekey_failure_count")
      .remove("pref_signed_prekey_registered")
      .remove("pref_next_pre_key_id")
      .remove("pref_gcm_password")
      .remove("pref_gcm_registered")
      .remove("pref_local_registration_id")
      .remove("pref_gcm_disabled")
      .remove("pref_gcm_registration_id")
      .remove("pref_gcm_registration_id_version")
      .remove("pref_gcm_registration_id_last_set_time")
      .commit()
  }

  /** Do not alter. If you need to migrate more stuff, create a new method. */
  private fun migrateFromSharedPrefsV3(context: Context) {
    Log.i(TAG, "[V3] Migrating account values from shared prefs.")

    putBoolean(KEY_HAS_LINKED_DEVICES, TextSecurePreferences.getBooleanPreference(context, "pref_multi_device", false))
  }

  private fun SharedPreferences.hasStringData(key: String): Boolean {
    return this.getString(key, null) != null
  }

  enum class UsernameSyncState(private val value: Long) {
    /** Our username data is in sync with the service */
    IN_SYNC(1),

    /** Both our username and username link are out-of-sync with the service */
    USERNAME_AND_LINK_CORRUPTED(2),

    /** Our username link is out-of-sync with the service */
    LINK_CORRUPTED(3);

    fun serialize(): Long = value

    companion object {
      fun deserialize(value: Long): UsernameSyncState {
        return entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Invalid value: $value")
      }
    }
  }
}

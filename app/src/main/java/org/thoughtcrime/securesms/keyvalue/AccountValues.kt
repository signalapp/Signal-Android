package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.MasterCipher
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceIds
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.lang.IllegalStateException
import java.security.SecureRandom

internal class AccountValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

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
    private const val KEY_ACI_SIGNED_PREKEY_FAILURE_COUNT = "account.aci_signed_prekey_failure_count"
    private const val KEY_ACI_NEXT_ONE_TIME_PREKEY_ID = "account.aci_next_one_time_prekey_id"

    private const val KEY_PNI_IDENTITY_PUBLIC_KEY = "account.pni_identity_public_key"
    private const val KEY_PNI_IDENTITY_PRIVATE_KEY = "account.pni_identity_private_key"
    private const val KEY_PNI_SIGNED_PREKEY_REGISTERED = "account.pni_signed_prekey_registered"
    private const val KEY_PNI_NEXT_SIGNED_PREKEY_ID = "account.pni_next_signed_prekey_id"
    private const val KEY_PNI_ACTIVE_SIGNED_PREKEY_ID = "account.pni_active_signed_prekey_id"
    private const val KEY_PNI_SIGNED_PREKEY_FAILURE_COUNT = "account.pni_signed_prekey_failure_count"
    private const val KEY_PNI_NEXT_ONE_TIME_PREKEY_ID = "account.pni_next_one_time_prekey_id"

    @VisibleForTesting
    const val KEY_E164 = "account.e164"
    @VisibleForTesting
    const val KEY_ACI = "account.aci"
    @VisibleForTesting
    const val KEY_PNI = "account.pni"
    @VisibleForTesting
    const val KEY_IS_REGISTERED = "account.is_registered"
  }

  init {
    if (!store.containsKey(KEY_ACI)) {
      migrateFromSharedPrefsV1(ApplicationDependencies.getApplication())
    }

    if (!store.containsKey(KEY_ACI_IDENTITY_PUBLIC_KEY)) {
      migrateFromSharedPrefsV2(ApplicationDependencies.getApplication())
    }
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      KEY_ACI_IDENTITY_PUBLIC_KEY,
      KEY_ACI_IDENTITY_PRIVATE_KEY,
      KEY_PNI_IDENTITY_PUBLIC_KEY,
      KEY_PNI_IDENTITY_PRIVATE_KEY,
    )
  }

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
        Log.w(TAG, "Tried to generate an ANI identity, but one was already set!", Throwable())
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
    override var signedPreKeyFailureCount: Int by integerValue(KEY_ACI_SIGNED_PREKEY_FAILURE_COUNT, 0)
    override var nextOneTimePreKeyId: Int by integerValue(KEY_ACI_NEXT_ONE_TIME_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
  }

  @get:JvmName("pniPreKeys")
  val pniPreKeys: PreKeyMetadataStore = object : PreKeyMetadataStore {
    override var nextSignedPreKeyId: Int by integerValue(KEY_PNI_NEXT_SIGNED_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
    override var activeSignedPreKeyId: Int by integerValue(KEY_PNI_ACTIVE_SIGNED_PREKEY_ID, -1)
    override var isSignedPreKeyRegistered: Boolean by booleanValue(KEY_PNI_SIGNED_PREKEY_REGISTERED, false)
    override var signedPreKeyFailureCount: Int by integerValue(KEY_PNI_SIGNED_PREKEY_FAILURE_COUNT, 0)
    override var nextOneTimePreKeyId: Int by integerValue(KEY_PNI_NEXT_ONE_TIME_PREKEY_ID, SecureRandom().nextInt(Medium.MAX_VALUE))
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

    ApplicationDependencies.getIncomingMessageObserver().notifyRegistrationChanged()

    if (previous != registered) {
      Recipient.self().live().refresh()
    }

    if (previous && !registered) {
      clearLocalCredentials()
    }
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

  private fun clearLocalCredentials() {
    putString(KEY_SERVICE_PASSWORD, Util.getSecret(18))

    val newProfileKey = ProfileKeyUtil.createNew()
    val self = Recipient.self()

    SignalDatabase.recipients.setProfileKey(self.id, newProfileKey)
    ApplicationDependencies.getGroupsV2Authorization().clear()
  }

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
      .putInteger(KEY_ACI_SIGNED_PREKEY_FAILURE_COUNT, defaultPrefs.getInt("pref_signed_prekey_failure_count", 0))
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

  private fun SharedPreferences.hasStringData(key: String): Boolean {
    return this.getString(key, null) != null
  }
}

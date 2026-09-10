package org.thoughtcrime.securesms.keyvalue

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.thoughtcrime.securesms.BuildConfig

/**
 * There are some values that you can't store in the normal encrypted [KeyValueStore].
 * Usually, it's because the value you're storing is needed to *open* the database that backs [SignalStore],
 * or is otherwise related to the state of the database itself. Regardless, this is just a normal
 * shared-prefs-backed class.
 */
object PlainTextKeyValueStore {

  const val SMS_MIGRATION_ID_OFFSET = "sms_migration_id_offset"

  private const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
  private const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
  private const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
  private const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
  private const val APP_MIGRATION_VERSION = "pref_app_migration_version"
  private const val LAST_VERSION_CODE = "last_version_code"
  private const val LANGUAGE = "pref_language"

  private lateinit var sharedPrefs: SharedPreferences

  /**
   * Must be called from [android.app.Application.attachBaseContext] before anything else has a chance to read from
   * this store.
   */
  @JvmStatic
  fun init(context: Context) {
    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
  }

  /** Stores the ID offset that was determined during the big migration that moved all SMS messages into the MMS table. */
  @JvmStatic
  var smsMigrationIdOffset: Long
    get() = sharedPrefs.getLong(SMS_MIGRATION_ID_OFFSET, -1)
    set(value) = sharedPrefs.edit(commit = true) { putLong(SMS_MIGRATION_ID_OFFSET, value) }

  /** The keystore-sealed secret used to open the main database. */
  @JvmStatic
  var databaseEncryptedSecret: String?
    get() = sharedPrefs.getString(DATABASE_ENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(DATABASE_ENCRYPTED_SECRET, value) }

  /** Legacy plaintext database secret, only present until it's been sealed into [databaseEncryptedSecret]. */
  @JvmStatic
  var databaseLegacyUnencryptedSecret: String?
    get() = sharedPrefs.getString(DATABASE_UNENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(DATABASE_UNENCRYPTED_SECRET, value) }

  /** The keystore-sealed secret used to decrypt attachments on disk. */
  @JvmStatic
  var attachmentEncryptedSecret: String?
    get() = sharedPrefs.getString(ATTACHMENT_ENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(ATTACHMENT_ENCRYPTED_SECRET, value) }

  /** Legacy plaintext attachment secret, only present until it's been sealed into [attachmentEncryptedSecret]. */
  @JvmStatic
  var attachmentLegacyUnencryptedSecret: String?
    get() = sharedPrefs.getString(ATTACHMENT_UNENCRYPTED_SECRET, null)
    set(value) = sharedPrefs.edit(commit = true) { putString(ATTACHMENT_UNENCRYPTED_SECRET, value) }

  /** The last [org.thoughtcrime.securesms.migrations.ApplicationMigrations] version that ran to completion. */
  @JvmStatic
  var appMigrationVersion: Int
    get() = sharedPrefs.getInt(APP_MIGRATION_VERSION, 1)
    set(value) = sharedPrefs.edit(commit = true) { putInt(APP_MIGRATION_VERSION, value) }

  /** The version code the app was last launched at. */
  @JvmStatic
  var lastVersionCode: Int
    get() = sharedPrefs.getInt(LAST_VERSION_CODE, BuildConfig.VERSION_CODE)
    set(value) = sharedPrefs.edit(commit = true) { putInt(LAST_VERSION_CODE, value) }

  /** The user's chosen app language, or "zz" to follow the system. */
  @JvmStatic
  var language: String
    get() = sharedPrefs.getString(LANGUAGE, "zz") ?: "zz"
    set(value) = sharedPrefs.edit(commit = true) { putString(LANGUAGE, value) }
}

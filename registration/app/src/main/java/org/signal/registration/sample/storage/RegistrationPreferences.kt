/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.storage

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.NewRegistrationData
import org.signal.registration.PreExistingRegistrationData

/**
 * SharedPreferences-based storage for registration data that doesn't need
 * the complexity of a SQLite database.
 */
object RegistrationPreferences {

  private lateinit var context: Application

  private const val PREFS_NAME = "registration_prefs"

  private const val KEY_E164 = "e164"
  private const val KEY_ACI = "aci"
  private const val KEY_PNI = "pni"
  private const val KEY_SERVICE_PASSWORD = "service_password"
  private const val KEY_AEP = "aep"
  private const val KEY_PROFILE_KEY = "profile_key"
  private const val KEY_ACI_REGISTRATION_ID = "aci_registration_id"
  private const val KEY_PNI_REGISTRATION_ID = "pni_registration_id"
  private const val KEY_ACI_IDENTITY_KEY = "aci_identity_key"
  private const val KEY_PNI_IDENTITY_KEY = "pni_identity_key"
  private const val KEY_TEMPORARY_MASTER_KEY = "temporary_master_key"
  private const val KEY_REGISTRATION_LOCK_ENABLED = "registration_lock_enabled"
  private const val KEY_PIN = "has_pin"
  private const val KEY_PIN_ALPHANUMERIC = "pin_alphanumeric"
  private const val KEY_PINS_OPTED_OUT = "pins_opted_out"

  fun init(context: Application) {
    this.context = context
  }

  private val prefs: SharedPreferences by lazy {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  var e164: String?
    get() = prefs.getString(KEY_E164, null)
    set(value) = prefs.edit { putString(KEY_E164, value) }

  var aci: ACI?
    get() = prefs.getString(KEY_ACI, null)?.let { ACI.parseOrNull(it) }
    set(value) = prefs.edit { putString(KEY_ACI, value?.toString()) }

  var pni: PNI?
    get() = prefs.getString(KEY_PNI, null)?.let { PNI.parseOrNull(it) }
    set(value) = prefs.edit { putString(KEY_PNI, value?.toString()) }

  var servicePassword: String?
    get() = prefs.getString(KEY_SERVICE_PASSWORD, null)
    set(value) = prefs.edit { putString(KEY_SERVICE_PASSWORD, value) }

  var aep: AccountEntropyPool?
    get() = prefs.getString(KEY_AEP, null)?.let { AccountEntropyPool(it) }
    set(value) = prefs.edit { putString(KEY_AEP, value?.toString()) }

  var profileKey: ProfileKey?
    get() = prefs.getString(KEY_PROFILE_KEY, null)?.let { ProfileKey(Base64.decode(it)) }
    set(value) = prefs.edit { putString(KEY_PROFILE_KEY, value?.let { Base64.encodeWithPadding(it.serialize()) }) }

  var aciRegistrationId: Int
    get() = prefs.getInt(KEY_ACI_REGISTRATION_ID, -1)
    set(value) = prefs.edit { putInt(KEY_ACI_REGISTRATION_ID, value) }

  var pniRegistrationId: Int
    get() = prefs.getInt(KEY_PNI_REGISTRATION_ID, -1)
    set(value) = prefs.edit { putInt(KEY_PNI_REGISTRATION_ID, value) }

  var aciIdentityKeyPair: IdentityKeyPair?
    get() = prefs.getString(KEY_ACI_IDENTITY_KEY, null)?.let { IdentityKeyPair(Base64.decode(it)) }
    set(value) = prefs.edit { putString(KEY_ACI_IDENTITY_KEY, value?.let { Base64.encodeWithPadding(it.serialize()) }) }

  var pniIdentityKeyPair: IdentityKeyPair?
    get() = prefs.getString(KEY_PNI_IDENTITY_KEY, null)?.let { IdentityKeyPair(Base64.decode(it)) }
    set(value) = prefs.edit { putString(KEY_PNI_IDENTITY_KEY, value?.let { Base64.encodeWithPadding(it.serialize()) }) }

  val masterKey: MasterKey?
    get() = aep?.deriveMasterKey()

  var temporaryMasterKey: MasterKey?
    get() = prefs.getString(KEY_TEMPORARY_MASTER_KEY, null)?.let { MasterKey(Base64.decode(it)) }
    set(value) = prefs.edit { putString(KEY_TEMPORARY_MASTER_KEY, value?.let { Base64.encodeWithPadding(it.serialize()) }) }

  var registrationLockEnabled: Boolean
    get() = prefs.getBoolean(KEY_REGISTRATION_LOCK_ENABLED, false)
    set(value) = prefs.edit { putBoolean(KEY_REGISTRATION_LOCK_ENABLED, value) }

  val hasPin: Boolean
    get() = pin != null

  var pin: String?
    get() = prefs.getString(KEY_PIN, null)
    set(value) = prefs.edit { putString(KEY_PIN, value) }

  var pinAlphanumeric: Boolean
    get() = prefs.getBoolean(KEY_PIN_ALPHANUMERIC, false)
    set(value) = prefs.edit { putBoolean(KEY_PIN_ALPHANUMERIC, value) }

  var pinsOptedOut: Boolean
    get() = prefs.getBoolean(KEY_PINS_OPTED_OUT, false)
    set(value) = prefs.edit { putBoolean(KEY_PINS_OPTED_OUT, value) }

  fun saveRegistrationData(data: NewRegistrationData) {
    prefs.edit {
      putString(KEY_E164, data.e164)
      putString(KEY_ACI, data.aci.toString())
      putString(KEY_PNI, data.pni.toString())
      putString(KEY_SERVICE_PASSWORD, data.servicePassword)
      putString(KEY_AEP, data.aep.value)
    }
  }

  fun getPreExistingRegistrationData(): PreExistingRegistrationData? {
    val e164 = e164 ?: return null
    val aci = aci ?: return null
    val pni = pni ?: return null
    val servicePassword = servicePassword ?: return null
    val aep = aep ?: return null

    return PreExistingRegistrationData(
      e164 = e164,
      aci = aci,
      pni = pni,
      servicePassword = servicePassword,
      aep = aep
    )
  }

  fun clearKeyMaterial() {
    prefs.edit {
      remove(KEY_PROFILE_KEY)
      remove(KEY_ACI_REGISTRATION_ID)
      remove(KEY_PNI_REGISTRATION_ID)
      remove(KEY_ACI_IDENTITY_KEY)
      remove(KEY_PNI_IDENTITY_KEY)
    }
  }

  fun clearAll() {
    prefs.edit { clear() }
  }
}

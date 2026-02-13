/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.data.AccountRegistrationResult
import org.thoughtcrime.securesms.registration.data.LocalRegistrationMetadataUtil
import org.thoughtcrime.securesms.registration.data.QuickstartCredentials
import org.thoughtcrime.securesms.registration.data.RegistrationData
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.whispersystems.signalservice.api.account.PreKeyCollection

/**
 * Reads pre-baked registration credentials from assets and performs
 * local registration, bypassing the normal registration flow.
 *
 * Follows the same pattern as [org.signal.benchmark.setup.TestUsers.setupSelf].
 */
object QuickstartInitializer {

  private val TAG = Log.tag(QuickstartInitializer::class.java)

  fun initialize(context: Context) {
    val credentialJson = findCredentialJson(context)
    if (credentialJson == null) {
      Log.w(TAG, "No quickstart credentials found in assets. Falling through to normal registration.")
      return
    }

    val credentials = Json.decodeFromString<QuickstartCredentials>(credentialJson)
    Log.i(TAG, "Loaded quickstart credentials for ${credentials.e164}")

    // Master secret setup
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(context, masterSecret)
    context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0).edit().putBoolean("passphrase_initialized", true).commit()

    // Set registration IDs from credentials
    SignalStore.account.registrationId = credentials.registrationId
    SignalStore.account.pniRegistrationId = credentials.pniRegistrationId

    // Decode pre-baked keys
    val aciIdentityKeyPair = IdentityKeyPair(Base64.decode(credentials.aciIdentityKeyPair, Base64.DEFAULT))
    val pniIdentityKeyPair = IdentityKeyPair(Base64.decode(credentials.pniIdentityKeyPair, Base64.DEFAULT))
    val aciSignedPreKey = SignedPreKeyRecord(Base64.decode(credentials.aciSignedPreKey, Base64.DEFAULT))
    val aciLastResortKyberPreKey = KyberPreKeyRecord(Base64.decode(credentials.aciLastResortKyberPreKey, Base64.DEFAULT))
    val pniSignedPreKey = SignedPreKeyRecord(Base64.decode(credentials.pniSignedPreKey, Base64.DEFAULT))
    val pniLastResortKyberPreKey = KyberPreKeyRecord(Base64.decode(credentials.pniLastResortKyberPreKey, Base64.DEFAULT))
    val profileKey = ProfileKey(Base64.decode(credentials.profileKey, Base64.DEFAULT))

    val registrationData = RegistrationData(
      code = "000000",
      e164 = credentials.e164,
      password = credentials.servicePassword,
      registrationId = credentials.registrationId,
      profileKey = profileKey,
      fcmToken = null,
      pniRegistrationId = credentials.pniRegistrationId,
      recoveryPassword = null
    )

    val remoteResult = AccountRegistrationResult(
      uuid = credentials.aci,
      pni = credentials.pni,
      storageCapable = false,
      number = credentials.e164,
      masterKey = null,
      pin = null,
      aciPreKeyCollection = PreKeyCollection(aciIdentityKeyPair.publicKey, aciSignedPreKey, aciLastResortKyberPreKey),
      pniPreKeyCollection = PreKeyCollection(pniIdentityKeyPair.publicKey, pniSignedPreKey, pniLastResortKyberPreKey),
      reRegistration = false
    )

    // Create metadata and register locally
    val localRegistrationData = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(
      aciIdentityKeyPair,
      pniIdentityKeyPair,
      registrationData,
      remoteResult,
      false
    )

    runBlocking {
      RegistrationRepository.registerAccountLocally(context, localRegistrationData)
    }

    // Enable FCM so the app fetches a token through its normal startup flow
    // rather than keeping a websocket open.
    SignalStore.account.fcmEnabled = true

    // Finalize registration state
    SignalStore.svr.optOut()
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
    SignalDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts(credentials.profileGivenName, credentials.profileFamilyName))
    RegistrationUtil.maybeMarkRegistrationComplete()

    Log.i(TAG, "Quickstart initialization complete for ${credentials.e164}")
  }

  private fun findCredentialJson(context: Context): String? {
    return try {
      val files = context.assets.list("quickstart") ?: return null
      val jsonFile = files.firstOrNull { it.endsWith(".json") } ?: return null
      context.assets.open("quickstart/$jsonFile").bufferedReader().readText()
    } catch (e: Exception) {
      Log.w(TAG, "Error reading quickstart credentials", e)
      null
    }
  }
}

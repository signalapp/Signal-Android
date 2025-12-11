/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.KeyMaterial
import org.signal.registration.NewRegistrationData
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.StorageController
import org.signal.registration.sample.storage.RegistrationDatabase
import org.signal.registration.sample.storage.RegistrationPreferences
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of [StorageController] that persists registration data using
 * SharedPreferences for simple key-value data and SQLite for prekeys.
 */
class RealStorageController(context: Context) : StorageController {

  private val db = RegistrationDatabase(context)

  override suspend fun generateAndStoreKeyMaterial(): KeyMaterial = withContext(Dispatchers.IO) {
    val accountEntropyPool = AccountEntropyPool.generate()
    val aciIdentityKeyPair = IdentityKeyPair.generate()
    val pniIdentityKeyPair = IdentityKeyPair.generate()

    val aciSignedPreKeyId = generatePreKeyId()
    val pniSignedPreKeyId = generatePreKeyId()
    val aciKyberPreKeyId = generatePreKeyId()
    val pniKyberPreKeyId = generatePreKeyId()

    val timestamp = System.currentTimeMillis()

    val aciSignedPreKey = generateSignedPreKey(aciSignedPreKeyId, timestamp, aciIdentityKeyPair)
    val pniSignedPreKey = generateSignedPreKey(pniSignedPreKeyId, timestamp, pniIdentityKeyPair)
    val aciLastResortKyberPreKey = generateKyberPreKey(aciKyberPreKeyId, timestamp, aciIdentityKeyPair)
    val pniLastResortKyberPreKey = generateKyberPreKey(pniKyberPreKeyId, timestamp, pniIdentityKeyPair)

    val aciRegistrationId = generateRegistrationId()
    val pniRegistrationId = generateRegistrationId()
    val profileKey = generateProfileKey()
    val unidentifiedAccessKey = deriveUnidentifiedAccessKey(profileKey)
    val password = generatePassword()

    val keyMaterial = KeyMaterial(
      aciIdentityKeyPair = aciIdentityKeyPair,
      aciSignedPreKey = aciSignedPreKey,
      aciLastResortKyberPreKey = aciLastResortKyberPreKey,
      pniIdentityKeyPair = pniIdentityKeyPair,
      pniSignedPreKey = pniSignedPreKey,
      pniLastResortKyberPreKey = pniLastResortKyberPreKey,
      aciRegistrationId = aciRegistrationId,
      pniRegistrationId = pniRegistrationId,
      unidentifiedAccessKey = unidentifiedAccessKey,
      servicePassword = password,
      accountEntropyPool = accountEntropyPool
    )

    storeKeyMaterial(keyMaterial, profileKey)

    keyMaterial
  }

  override suspend fun saveNewRegistrationData(newRegistrationData: NewRegistrationData) = withContext(Dispatchers.IO) {
    RegistrationPreferences.saveRegistrationData(newRegistrationData)
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.IO) {
    RegistrationPreferences.getPreExistingRegistrationData()
  }

  override suspend fun clearAllData() = withContext(Dispatchers.IO) {
    RegistrationPreferences.clearAll()
    db.clearAllPreKeys()
  }

  private fun storeKeyMaterial(keyMaterial: KeyMaterial, profileKey: ProfileKey) {
    // Clear existing data
    RegistrationPreferences.clearKeyMaterial()
    db.clearAllPreKeys()

    // Store in SharedPreferences
    RegistrationPreferences.aciIdentityKeyPair = keyMaterial.aciIdentityKeyPair
    RegistrationPreferences.pniIdentityKeyPair = keyMaterial.pniIdentityKeyPair
    RegistrationPreferences.aciRegistrationId = keyMaterial.aciRegistrationId
    RegistrationPreferences.pniRegistrationId = keyMaterial.pniRegistrationId
    RegistrationPreferences.profileKey = profileKey

    // Store prekeys in database
    db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, keyMaterial.aciSignedPreKey)
    db.signedPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, keyMaterial.pniSignedPreKey)
    db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_ACI, keyMaterial.aciLastResortKyberPreKey)
    db.kyberPreKeys.insert(RegistrationDatabase.ACCOUNT_TYPE_PNI, keyMaterial.pniLastResortKyberPreKey)
  }

  private fun generateSignedPreKey(id: Int, timestamp: Long, identityKeyPair: IdentityKeyPair): SignedPreKeyRecord {
    val keyPair = ECKeyPair.generate()
    val signature = identityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize())
    return SignedPreKeyRecord(id, timestamp, keyPair, signature)
  }

  private fun generateKyberPreKey(id: Int, timestamp: Long, identityKeyPair: IdentityKeyPair): KyberPreKeyRecord {
    val kemKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
    val signature = identityKeyPair.privateKey.calculateSignature(kemKeyPair.publicKey.serialize())
    return KyberPreKeyRecord(id, timestamp, kemKeyPair, signature)
  }

  private fun generatePreKeyId(): Int {
    return SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1
  }

  private fun generateRegistrationId(): Int {
    return SecureRandom().nextInt(16380) + 1
  }

  private fun generateProfileKey(): ProfileKey {
    val keyBytes = ByteArray(32)
    SecureRandom().nextBytes(keyBytes)
    return ProfileKey(keyBytes)
  }

  /**
   * Generates a password for basic auth during registration.
   * 18 random bytes, base64 encoded with padding.
   */
  private fun generatePassword(): String {
    val passwordBytes = ByteArray(18)
    SecureRandom().nextBytes(passwordBytes)
    return Base64.encodeWithPadding(passwordBytes)
  }

  /**
   * Derives the unidentified access key from a profile key.
   * This mirrors the logic in UnidentifiedAccess.deriveAccessKeyFrom().
   */
  private fun deriveUnidentifiedAccessKey(profileKey: ProfileKey): ByteArray {
    val nonce = ByteArray(12)
    val input = ByteArray(16)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(profileKey.serialize(), "AES"), GCMParameterSpec(128, nonce))

    val ciphertext = cipher.doFinal(input)
    return ciphertext.copyOf(16)
  }
}

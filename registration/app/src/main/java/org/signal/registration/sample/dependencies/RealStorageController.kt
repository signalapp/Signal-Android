/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ServiceId
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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of [StorageController] that persists registration data to a SQLite database.
 */
class RealStorageController(context: Context) : StorageController {

  private val db: RegistrationDatabase = RegistrationDatabase(context)

  override suspend fun generateAndStoreKeyMaterial(): KeyMaterial = withContext(Dispatchers.IO) {
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
      servicePassword = password
    )

    storeKeyMaterial(keyMaterial, profileKey)

    keyMaterial
  }

  override suspend fun saveNewRegistrationData(newRegistrationData: NewRegistrationData) = withContext(Dispatchers.IO) {
    val database = db.writableDatabase
    database.beginTransaction()
    try {
      database.delete(RegistrationDatabase.TABLE_ACCOUNT, null, null)

      database.insert(
        RegistrationDatabase.TABLE_ACCOUNT,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_E164, newRegistrationData.e164)
          put(RegistrationDatabase.COLUMN_ACI, newRegistrationData.aci.toString())
          put(RegistrationDatabase.COLUMN_PNI, newRegistrationData.pni.toString())
          put(RegistrationDatabase.COLUMN_SERVICE_PASSWORD, newRegistrationData.servicePassword)
          put(RegistrationDatabase.COLUMN_AEP, newRegistrationData.aep.toString())
        }
      )

      database.setTransactionSuccessful()
    } finally {
      database.endTransaction()
    }
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? = withContext(Dispatchers.IO) {
    val database = db.readableDatabase
    val cursor = database.query(
      RegistrationDatabase.TABLE_ACCOUNT,
      arrayOf(
        RegistrationDatabase.COLUMN_E164,
        RegistrationDatabase.COLUMN_ACI,
        RegistrationDatabase.COLUMN_PNI,
        RegistrationDatabase.COLUMN_SERVICE_PASSWORD,
        RegistrationDatabase.COLUMN_AEP
      ),
      null,
      null,
      null,
      null,
      null
    )

    cursor.use {
      if (it.moveToFirst()) {
        val e164 = it.getString(it.getColumnIndexOrThrow(RegistrationDatabase.COLUMN_E164))
        val aciString = it.getString(it.getColumnIndexOrThrow(RegistrationDatabase.COLUMN_ACI))
        val pniString = it.getString(it.getColumnIndexOrThrow(RegistrationDatabase.COLUMN_PNI))
        val servicePassword = it.getString(it.getColumnIndexOrThrow(RegistrationDatabase.COLUMN_SERVICE_PASSWORD))
        val aepValue = it.getString(it.getColumnIndexOrThrow(RegistrationDatabase.COLUMN_AEP))

        PreExistingRegistrationData(
          e164 = e164,
          aci = ServiceId.Aci.parseFromString(aciString),
          pni = ServiceId.Pni.parseFromString(pniString),
          servicePassword = servicePassword,
          aep = AccountEntropyPool(aepValue)
        )
      } else {
        null
      }
    }
  }

  private fun storeKeyMaterial(keyMaterial: KeyMaterial, profileKey: ProfileKey) {
    val database = db.writableDatabase
    database.beginTransaction()
    try {
      // Clear any existing data
      database.delete(RegistrationDatabase.TABLE_IDENTITY_KEYS, null, null)
      database.delete(RegistrationDatabase.TABLE_SIGNED_PREKEYS, null, null)
      database.delete(RegistrationDatabase.TABLE_KYBER_PREKEYS, null, null)
      database.delete(RegistrationDatabase.TABLE_REGISTRATION_IDS, null, null)
      database.delete(RegistrationDatabase.TABLE_PROFILE_KEY, null, null)

      // Store ACI identity key
      database.insert(
        RegistrationDatabase.TABLE_IDENTITY_KEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_ACI)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.aciIdentityKeyPair.serialize())
        }
      )

      // Store PNI identity key
      database.insert(
        RegistrationDatabase.TABLE_IDENTITY_KEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_PNI)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.pniIdentityKeyPair.serialize())
        }
      )

      // Store ACI signed pre-key
      database.insert(
        RegistrationDatabase.TABLE_SIGNED_PREKEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_ACI)
          put(RegistrationDatabase.COLUMN_KEY_ID, keyMaterial.aciSignedPreKey.id)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.aciSignedPreKey.serialize())
        }
      )

      // Store PNI signed pre-key
      database.insert(
        RegistrationDatabase.TABLE_SIGNED_PREKEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_PNI)
          put(RegistrationDatabase.COLUMN_KEY_ID, keyMaterial.pniSignedPreKey.id)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.pniSignedPreKey.serialize())
        }
      )

      // Store ACI Kyber pre-key
      database.insert(
        RegistrationDatabase.TABLE_KYBER_PREKEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_ACI)
          put(RegistrationDatabase.COLUMN_KEY_ID, keyMaterial.aciLastResortKyberPreKey.id)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.aciLastResortKyberPreKey.serialize())
        }
      )

      // Store PNI Kyber pre-key
      database.insert(
        RegistrationDatabase.TABLE_KYBER_PREKEYS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_PNI)
          put(RegistrationDatabase.COLUMN_KEY_ID, keyMaterial.pniLastResortKyberPreKey.id)
          put(RegistrationDatabase.COLUMN_KEY_DATA, keyMaterial.pniLastResortKyberPreKey.serialize())
        }
      )

      // Store ACI registration ID
      database.insert(
        RegistrationDatabase.TABLE_REGISTRATION_IDS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_ACI)
          put(RegistrationDatabase.COLUMN_REGISTRATION_ID, keyMaterial.aciRegistrationId)
        }
      )

      // Store PNI registration ID
      database.insert(
        RegistrationDatabase.TABLE_REGISTRATION_IDS,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_ACCOUNT_TYPE, ACCOUNT_TYPE_PNI)
          put(RegistrationDatabase.COLUMN_REGISTRATION_ID, keyMaterial.pniRegistrationId)
        }
      )

      // Store profile key
      database.insert(
        RegistrationDatabase.TABLE_PROFILE_KEY,
        null,
        ContentValues().apply {
          put(RegistrationDatabase.COLUMN_KEY_DATA, profileKey.serialize())
        }
      )

      database.setTransactionSuccessful()
    } finally {
      database.endTransaction()
    }
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

  companion object {
    private const val ACCOUNT_TYPE_ACI = "aci"
    private const val ACCOUNT_TYPE_PNI = "pni"
  }

  private class RegistrationDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
  ) {
    companion object {
      const val DATABASE_NAME = "registration.db"
      const val DATABASE_VERSION = 1

      const val TABLE_IDENTITY_KEYS = "identity_keys"
      const val TABLE_SIGNED_PREKEYS = "signed_prekeys"
      const val TABLE_KYBER_PREKEYS = "kyber_prekeys"
      const val TABLE_REGISTRATION_IDS = "registration_ids"
      const val TABLE_PROFILE_KEY = "profile_key"
      const val TABLE_ACCOUNT = "account"

      const val COLUMN_ID = "_id"
      const val COLUMN_ACCOUNT_TYPE = "account_type"
      const val COLUMN_KEY_ID = "key_id"
      const val COLUMN_KEY_DATA = "key_data"
      const val COLUMN_REGISTRATION_ID = "registration_id"
      const val COLUMN_E164 = "e164"
      const val COLUMN_ACI = "aci"
      const val COLUMN_PNI = "pni"
      const val COLUMN_SERVICE_PASSWORD = "service_password"
      const val COLUMN_AEP = "aep"
    }

    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE $TABLE_IDENTITY_KEYS (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_ACCOUNT_TYPE TEXT NOT NULL UNIQUE,
          $COLUMN_KEY_DATA BLOB NOT NULL
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE $TABLE_SIGNED_PREKEYS (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_ACCOUNT_TYPE TEXT NOT NULL,
          $COLUMN_KEY_ID INTEGER NOT NULL,
          $COLUMN_KEY_DATA BLOB NOT NULL
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE $TABLE_KYBER_PREKEYS (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_ACCOUNT_TYPE TEXT NOT NULL,
          $COLUMN_KEY_ID INTEGER NOT NULL,
          $COLUMN_KEY_DATA BLOB NOT NULL
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE $TABLE_REGISTRATION_IDS (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_ACCOUNT_TYPE TEXT NOT NULL UNIQUE,
          $COLUMN_REGISTRATION_ID INTEGER NOT NULL
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE $TABLE_PROFILE_KEY (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_KEY_DATA BLOB NOT NULL
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE $TABLE_ACCOUNT (
          $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COLUMN_E164 TEXT NOT NULL,
          $COLUMN_ACI TEXT NOT NULL,
          $COLUMN_PNI TEXT NOT NULL,
          $COLUMN_SERVICE_PASSWORD TEXT NOT NULL,
          $COLUMN_AEP TEXT NOT NULL
        )
        """.trimIndent()
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      // No migrations needed yet
    }
  }
}

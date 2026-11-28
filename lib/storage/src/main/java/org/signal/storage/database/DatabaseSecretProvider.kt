/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.database

import android.content.Context
import org.signal.core.util.crypto.KeyStoreHelper
import org.signal.storage.keyvalue.PlainTextKeyValueStore
import java.io.IOException
import java.security.SecureRandom

/**
 * It can be rather expensive to read from the keystore, so this class caches the key in memory
 * after it is created.
 */
object DatabaseSecretProvider {

  @Volatile
  private var instance: DatabaseSecret? = null

  @JvmStatic
  fun getOrCreateDatabaseSecret(context: Context): DatabaseSecret {
    if (instance == null) {
      synchronized(this) {
        if (instance == null) {
          instance = getOrCreate()
        }
      }
    }
    return instance!!
  }

  private fun getOrCreate(): DatabaseSecret {
    val unencryptedSecret = PlainTextKeyValueStore.databaseLegacyUnencryptedSecret
    val encryptedSecret = PlainTextKeyValueStore.databaseEncryptedSecret

    return when {
      unencryptedSecret != null -> getUnencryptedDatabaseSecret(unencryptedSecret)
      encryptedSecret != null -> getEncryptedDatabaseSecret(encryptedSecret)
      else -> createAndStoreDatabaseSecret()
    }
  }

  private fun getUnencryptedDatabaseSecret(unencryptedSecret: String): DatabaseSecret {
    try {
      val databaseSecret = DatabaseSecret.fromHex(unencryptedSecret)
      val encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes())

      PlainTextKeyValueStore.databaseEncryptedSecret = encryptedSecret.serialize()
      PlainTextKeyValueStore.databaseLegacyUnencryptedSecret = null

      return databaseSecret
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  private fun getEncryptedDatabaseSecret(serializedEncryptedSecret: String): DatabaseSecret {
    val encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret)
    return DatabaseSecret.fromBytes(KeyStoreHelper.unseal(encryptedSecret))
  }

  private fun createAndStoreDatabaseSecret(): DatabaseSecret {
    val secret = ByteArray(32)
    SecureRandom().nextBytes(secret)

    val databaseSecret = DatabaseSecret.fromBytes(secret)
    val encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes())

    PlainTextKeyValueStore.databaseEncryptedSecret = encryptedSecret.serialize()

    return databaseSecret
  }
}

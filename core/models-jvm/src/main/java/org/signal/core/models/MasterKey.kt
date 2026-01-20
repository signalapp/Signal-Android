/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.core.models

import org.signal.core.models.storageservice.StorageKey
import org.signal.core.util.Base64
import org.signal.core.util.CryptoUtil
import org.signal.core.util.Hex
import java.security.SecureRandom

class MasterKey(masterKey: ByteArray) {
  private val masterKey: ByteArray

  companion object {
    private const val LENGTH = 32

    fun createNew(secureRandom: SecureRandom): MasterKey {
      val key = ByteArray(LENGTH)
      secureRandom.nextBytes(key)
      return MasterKey(key)
    }
  }

  init {
    check(masterKey.size == LENGTH) { "Master key must be $LENGTH bytes long (actualSize: ${masterKey.size})" }
    this.masterKey = masterKey
  }

  fun deriveRegistrationLock(): String {
    return Hex.toStringCondensed(derive("Registration Lock"))
  }

  fun deriveRegistrationRecoveryPassword(): String {
    return Base64.encodeWithPadding(derive("Registration Recovery")!!)
  }

  fun deriveStorageServiceKey(): StorageKey {
    return StorageKey(derive("Storage Service Encryption")!!)
  }

  fun deriveLoggingKey(): ByteArray? {
    return derive("Logging Key")
  }

  private fun derive(keyName: String): ByteArray? {
    return CryptoUtil.hmacSha256(masterKey, keyName.toByteArray(Charsets.UTF_8))
  }

  fun serialize(): ByteArray {
    return masterKey.clone()
  }

  override fun equals(o: Any?): Boolean {
    if (o == null || o.javaClass != javaClass) return false

    return (o as MasterKey).masterKey.contentEquals(masterKey)
  }

  override fun hashCode(): Int {
    return masterKey.contentHashCode()
  }

  override fun toString(): String {
    return "MasterKey(xxx)"
  }
}

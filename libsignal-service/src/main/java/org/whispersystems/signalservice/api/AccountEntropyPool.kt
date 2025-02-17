/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.signal.libsignal.messagebackup.AccountEntropyPool as LibSignalAccountEntropyPool

/**
 * The Root of All Entropy. You can use this to derive the [MasterKey] or [MessageBackupKey].
 */
class AccountEntropyPool(val value: String) {

  companion object {
    private val INVALID_CHARACTERS = Regex("[^0-9a-zA-Z]")
    const val LENGTH = 64

    fun generate(): AccountEntropyPool {
      return AccountEntropyPool(LibSignalAccountEntropyPool.generate())
    }

    fun parseOrNull(input: String): AccountEntropyPool? {
      val stripped = removeIllegalCharacters(input)
      if (stripped.length != LENGTH) {
        return null
      }

      return AccountEntropyPool(stripped)
    }

    fun isFullyValid(input: String): Boolean {
      return LibSignalAccountEntropyPool.isValid(input)
    }

    fun removeIllegalCharacters(input: String): String {
      return input.replace(INVALID_CHARACTERS, "")
    }
  }

  fun deriveMasterKey(): MasterKey {
    return MasterKey(LibSignalAccountEntropyPool.deriveSvrKey(value))
  }

  fun deriveMessageBackupKey(): MessageBackupKey {
    val libSignalBackupKey = LibSignalAccountEntropyPool.deriveBackupKey(value)
    return MessageBackupKey(libSignalBackupKey.serialize())
  }
}

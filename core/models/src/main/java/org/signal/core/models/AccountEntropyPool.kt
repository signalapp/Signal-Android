/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models

import org.signal.core.models.backup.MessageBackupKey

private typealias LibSignalAccountEntropyPool = org.signal.libsignal.messagebackup.AccountEntropyPool

/**
 * The Root of All Entropy. You can use this to derive the [org.whispersystems.signalservice.api.kbs.MasterKey] or [org.whispersystems.signalservice.api.backup.MessageBackupKey].
 */
class AccountEntropyPool(value: String) {

  val value = value.lowercase()
  val displayValue = value.uppercase()

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

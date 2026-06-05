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
  val displayValue = formatForDisplay(value.uppercase())

  companion object {
    const val LENGTH = 64

    /**
     * Maps storage characters (left) to their display equivalents (right). Used to swap
     * characters that are visually ambiguous in some fonts (the letter 'O' vs the digit '0')
     * for unambiguous symbols when shown to users. Add new pairs here as needed; the format
     * helpers and [displayValue] will pick them up automatically.
     */
    val CHARACTER_DISPLAY_MAP: Map<Char, Char> = mapOf(
      'O' to '#',
      '0' to '='
    )

    private val STORAGE_TO_DISPLAY: Map<Char, Char> = CHARACTER_DISPLAY_MAP
    private val DISPLAY_TO_STORAGE: Map<Char, Char> = CHARACTER_DISPLAY_MAP.entries.associate { (k, v) -> v to k }

    /** Storage charset (alphanumeric) plus any display characters from [CHARACTER_DISPLAY_MAP]. */
    private val INVALID_CHARACTERS: Regex = run {
      val extras = CHARACTER_DISPLAY_MAP.values.joinToString("") { Regex.escape(it.toString()) }
      Regex("[^0-9a-zA-Z$extras]")
    }

    fun generate(): AccountEntropyPool {
      return AccountEntropyPool(LibSignalAccountEntropyPool.generate())
    }

    fun parseOrNull(input: String): AccountEntropyPool? {
      val stripped = removeIllegalCharacters(formatForStorage(input))
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

    /**
     * Converts a storage-form AEP string into the form that should be shown to users (and stored
     * in password managers) by applying [CHARACTER_DISPLAY_MAP]. Lookup is case-insensitive, so
     * a lowercase storage character (e.g. 'o') is formatted the same as its uppercase counterpart
     * ('O'). Characters not in the map are passed through unchanged.
     */
    fun formatForDisplay(input: String): String {
      if (STORAGE_TO_DISPLAY.isEmpty()) {
        return input
      }

      return buildString(input.length) {
        for (c in input) {
          val swap = STORAGE_TO_DISPLAY[c] ?: STORAGE_TO_DISPLAY[c.uppercaseChar()]
          append(swap ?: c)
        }
      }
    }

    /**
     * Converts a user-supplied (display-form) AEP string back into its storage form by reversing
     * [CHARACTER_DISPLAY_MAP]. Characters not in the map are passed through unchanged.
     */
    fun formatForStorage(input: String): String {
      if (DISPLAY_TO_STORAGE.isEmpty()) {
        return input
      }

      return buildString(input.length) {
        for (c in input) append(DISPLAY_TO_STORAGE[c] ?: c)
      }
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

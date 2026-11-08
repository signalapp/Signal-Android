/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.shared

import org.signal.core.models.ServiceId.ACI

/**
 * The 8-4-4-4-12 layout an account ID shares with the [ACI] it is written from.
 * Exists to support formatting ACI's as-you-type.
 */
internal object AccountIdFormat {

  /** An account ID is an ACI with its dashes removed, so it is always this many hex characters. */
  const val ACCOUNT_ID_LENGTH = 32

  /** Offsets in a raw account ID that a dash is inserted in front of. */
  private val DASH_OFFSETS = intArrayOf(8, 12, 16, 20)

  /** Formatting the user may have pasted along with the account ID, which we accept and discard. */
  private val FORMATTING_CHARACTERS = Regex("""[\s-]""")

  /** The most digits an E164 phone number can have. Anything longer can't be a number, no matter how it's written. */
  private const val MAX_PHONE_NUMBER_DIGITS = 15

  /** Strips the formatting a user may have typed or pasted, leaving the raw form the account ID is stored in. */
  fun normalize(input: String): String = input.replace(FORMATTING_CHARACTERS, "").lowercase()

  /** [normalize]s [input] and cuts it down to [ACCOUNT_ID_LENGTH], since nothing longer than a complete ID can be entered. */
  fun normalizeAndTruncate(input: String): String = normalize(input).take(ACCOUNT_ID_LENGTH)

  /**
   * Reads [input] as a raw account ID, truncated to [ACCOUNT_ID_LENGTH], or null if it doesn't read as one. Only text
   * that couldn't plausibly be a phone number qualifies: it has to be entirely hex, and either contain a letter or be
   * longer than any E164 number.
   */
  fun asAccountIdOrNull(input: String): String? {
    val raw = normalize(input)

    if (raw.isEmpty() || !containsOnlyAccountIdCharacters(raw)) {
      return null
    }

    return if (raw.any { !it.isDigit() } || raw.length > MAX_PHONE_NUMBER_DIGITS) {
      raw.take(ACCOUNT_ID_LENGTH)
    } else {
      null
    }
  }

  /** Whether [text] is made up entirely of the characters an account ID can contain. */
  fun containsOnlyAccountIdCharacters(text: String): Boolean = text.all { it in '0'..'9' || it in 'a'..'f' }

  /** Why [accountId] can't be submitted, or null if there's nothing wrong with it. A too-short ID is not an error, since the user may be mid-entry. */
  fun validate(accountId: String): AccountIdError? {
    return if (containsOnlyAccountIdCharacters(accountId)) {
      null
    } else {
      AccountIdError.Invalid
    }
  }

  /**
   * Rewrites a raw account ID with the dashes and uppercasing a UUID is normally written with.
   */
  fun dashed(accountId: String): String {
    return buildString {
      for ((index, character) in accountId.withIndex()) {
        if (index in DASH_OFFSETS) {
          append('-')
        }
        // Uppercase on a per-character basis so as to not screw up offsets
        append(character.uppercaseChar())
      }
    }
  }

  /**
   * How many dashes [dashed] inserts before [offset] in an ID of [length] characters. A dash is only present if the ID
   * is long enough to have a character after it, so [length] decides which offsets actually contribute.
   */
  fun dashesBeforeRawOffset(offset: Int, length: Int): Int {
    return DASH_OFFSETS.count { it <= offset && it < length }
  }

  /** How many dashes precede [offset] in the output of [dashed] for an ID of [length] characters. */
  fun dashesBeforeDashedOffset(offset: Int, length: Int): Int {
    return DASH_OFFSETS.withIndex().count { (index, dashOffset) -> dashOffset < length && dashOffset + index < offset }
  }

  /** Parses a complete raw account ID as the [ACI] it stands for. Null if it isn't a valid ACI. */
  fun toAciOrNull(accountId: String): ACI? {
    if (accountId.length != ACCOUNT_ID_LENGTH) {
      return null
    }

    return ACI.parseOrNull(dashed(accountId))
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin

/**
 * A recovery key split into the fixed-size character groups it is always shown in, along with the ways those groups get
 * turned back into a string.
 */
@JvmInline
value class RecoveryKeyGroups private constructor(val groups: List<String>) {

  companion object {
    /** How many characters make up a single displayed group of a recovery key. */
    const val GROUP_SIZE = 4

    fun from(recoveryKey: String): RecoveryKeyGroups = RecoveryKeyGroups(recoveryKey.chunked(GROUP_SIZE))
  }

  /** The key with no separators, which is the form everything outside of the UI expects. */
  val flat: String
    get() = groups.joinToString(separator = "")

  /** The key as one space-separated string, for when the groups have to wrap as ordinary text. */
  val spaced: String
    get() = groups.joinToString(separator = " ")

  /** The groups laid out [perRow] at a time. */
  fun rows(perRow: Int): List<List<String>> = groups.chunked(perRow)
}

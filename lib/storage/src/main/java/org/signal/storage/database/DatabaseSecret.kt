/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.database

import org.signal.core.util.Hex
import java.io.IOException

class DatabaseSecret private constructor(private val key: ByteArray) {

  companion object {
    @JvmStatic
    fun fromBytes(key: ByteArray): DatabaseSecret {
      return DatabaseSecret(key)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun fromHex(hex: String): DatabaseSecret {
      return DatabaseSecret(Hex.fromStringCondensed(hex))
    }
  }

  fun asHex(): String = Hex.toStringCondensed(key)

  fun asBytes(): ByteArray = key
}

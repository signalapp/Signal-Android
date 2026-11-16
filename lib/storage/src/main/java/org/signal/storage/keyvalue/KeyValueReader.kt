/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

interface KeyValueReader {
  fun getBlob(key: String, defaultValue: ByteArray?): ByteArray?
  fun getBoolean(key: String, defaultValue: Boolean): Boolean
  fun getFloat(key: String, defaultValue: Float): Float
  fun getInteger(key: String, defaultValue: Int): Int
  fun getLong(key: String, defaultValue: Long): Long
  fun getString(key: String, defaultValue: String?): String?

  /** Like [getBlob], but throws if the stored value is null, or if the key is absent and there is no [defaultValue]. */
  fun getNonNullBlob(key: String, defaultValue: ByteArray? = null): ByteArray

  /** Like [getString], but throws if the stored value is null, or if the key is absent and there is no [defaultValue]. */
  fun getNonNullString(key: String, defaultValue: String? = null): String

  fun containsKey(key: String): Boolean
}

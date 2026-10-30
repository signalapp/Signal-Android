/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import androidx.preference.PreferenceDataStore

/**
 * An implementation of the [PreferenceDataStore] interface to let us link preference screens
 * to the key-value store.
 */
class SignalPreferenceDataStore(private val store: KeyValueStore) : PreferenceDataStore() {

  override fun putString(key: String, value: String?) {
    store.beginWrite().putString(key, value).apply()
  }

  override fun putInt(key: String, value: Int) {
    store.beginWrite().putInteger(key, value).apply()
  }

  override fun putLong(key: String, value: Long) {
    store.beginWrite().putLong(key, value).apply()
  }

  override fun putFloat(key: String, value: Float) {
    store.beginWrite().putFloat(key, value).apply()
  }

  override fun putBoolean(key: String, value: Boolean) {
    store.beginWrite().putBoolean(key, value).apply()
  }

  override fun getString(key: String, defValue: String?): String? {
    return store.getString(key, defValue)
  }

  override fun getInt(key: String, defValue: Int): Int {
    return store.getInteger(key, defValue)
  }

  override fun getLong(key: String, defValue: Long): Long {
    return store.getLong(key, defValue)
  }

  override fun getFloat(key: String, defValue: Float): Float {
    return store.getFloat(key, defValue)
  }

  override fun getBoolean(key: String, defValue: Boolean): Boolean {
    return store.getBoolean(key, defValue)
  }
}

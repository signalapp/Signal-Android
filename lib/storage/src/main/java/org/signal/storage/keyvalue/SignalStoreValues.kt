/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import com.squareup.wire.ProtoAdapter
import org.signal.core.util.ByteSerializer
import org.signal.core.util.StringSerializer
import org.signal.storage.protos.SignalStoreList
import java.io.IOException

abstract class SignalStoreValues protected constructor(val store: KeyValueStore) {

  abstract fun onFirstEverAppLaunch()

  abstract val keysToIncludeInBackup: List<String>

  /** See [KeyValueStore.addChangeListener]. */
  fun <T> addChangeListener(key: String, defaultValue: T, listener: KeyValueStore.ChangeListener<T>) {
    store.addChangeListener(key, defaultValue, listener)
  }

  /** See [KeyValueStore.addChangeListener]. */
  fun <S, T> addChangeListener(key: String, defaultValue: S, mapper: (S) -> T, listener: KeyValueStore.ChangeListener<T>) {
    store.addChangeListener(key, defaultValue, mapper, listener)
  }

  /** Listens for changes to a proto stored at [key], decoding it with [adapter]. The listener receives [defaultValue] when the key is absent. */
  fun <M : Any> addChangeListener(key: String, defaultValue: M, adapter: ProtoAdapter<M>, listener: KeyValueStore.ChangeListener<M>) {
    store.addChangeListener<ByteArray?, M>(key, null, { blob -> blob?.let { adapter.decode(it) } ?: defaultValue }, listener)
  }

  protected fun getString(key: String, defaultValue: String?): String? {
    return store.getString(key, defaultValue)
  }

  protected fun getInteger(key: String, defaultValue: Int): Int {
    return store.getInteger(key, defaultValue)
  }

  protected fun getLong(key: String, defaultValue: Long): Long {
    return store.getLong(key, defaultValue)
  }

  protected fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return store.getBoolean(key, defaultValue)
  }

  protected fun getFloat(key: String, defaultValue: Float): Float {
    return store.getFloat(key, defaultValue)
  }

  protected fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? {
    return store.getBlob(key, defaultValue)
  }

  protected fun getNonNullString(key: String, defaultValue: String? = null): String {
    return store.getNonNullString(key, defaultValue)
  }

  protected fun getNonNullBlob(key: String, defaultValue: ByteArray? = null): ByteArray {
    return store.getNonNullBlob(key, defaultValue)
  }

  protected fun <T> getObject(key: String, defaultValue: T?, serializer: ByteSerializer<T>): T? {
    val blob = store.getBlob(key, null) ?: return defaultValue
    return serializer.deserialize(blob)
  }

  protected fun <T> getList(key: String, serializer: StringSerializer<T>): List<T> {
    val blob = getBlob(key, null) ?: return emptyList()

    try {
      return SignalStoreList.ADAPTER.decode(blob).contents.map { serializer.deserialize(it) }
    } catch (e: IOException) {
      throw IllegalArgumentException(e)
    }
  }

  protected fun putBlob(key: String, value: ByteArray?) {
    store.beginWrite().putBlob(key, value).apply()
  }

  protected fun putBoolean(key: String, value: Boolean) {
    store.beginWrite().putBoolean(key, value).apply()
  }

  protected fun putFloat(key: String, value: Float) {
    store.beginWrite().putFloat(key, value).apply()
  }

  protected fun putInteger(key: String, value: Int) {
    store.beginWrite().putInteger(key, value).apply()
  }

  protected fun putLong(key: String, value: Long) {
    store.beginWrite().putLong(key, value).apply()
  }

  protected fun putString(key: String, value: String?) {
    store.beginWrite().putString(key, value).apply()
  }

  protected fun <T> putObject(key: String, value: T, serializer: ByteSerializer<T>) {
    putBlob(key, serializer.serialize(value))
  }

  protected fun <T> putList(key: String, values: List<T>, serializer: StringSerializer<T>) {
    val encoded = SignalStoreList(contents = values.map { serializer.serialize(it) }).encode()
    putBlob(key, encoded)
  }

  protected fun remove(key: String) {
    store.beginWrite().remove(key).apply()
  }
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import androidx.lifecycle.SavedStateHandle
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Nullable SavedStateHandle delegate, which does not accept a default.
 */
fun <T> SavedStateHandle.delegate(key: String): ReadWriteProperty<Any?, T?> {
  return NullableSavedStateHandleDelegate(this, key)
}

/**
 * Non-null SavedStateHandle delegate with a default value. Recommended when the default
 * value has to be created on-demand. The default is NOT written to the SavedStateHandle.
 */
fun <T> SavedStateHandle.delegate(key: String, default: () -> T): ReadWriteProperty<Any?, T> {
  return DefaultSavedStateHandleDelegate(this, key, default)
}

/**
 * Convenience function for non-null SavedStateHandle delegate. Recommended when working
 * with primitive or pre-constructed objects. The default is NOT written to the SavedStateHandle.
 */
fun <T> SavedStateHandle.delegate(key: String, default: T): ReadWriteProperty<Any?, T> {
  return DefaultSavedStateHandleDelegate(this, key) { default }
}

private class NullableSavedStateHandleDelegate<T>(
  private val handle: SavedStateHandle,
  private val key: String
) : ReadWriteProperty<Any?, T?> {
  override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
    return handle[key]
  }

  override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
    handle[key] = value
  }
}

private class DefaultSavedStateHandleDelegate<T>(
  private val handle: SavedStateHandle,
  private val key: String,
  default: () -> T
) : ReadWriteProperty<Any?, T> {

  private val lazyDefault by lazy { default() }

  override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    return handle[key] ?: lazyDefault
  }

  override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    handle[key] = value
  }
}

/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlin.reflect.KProperty

/**
 * Identical to Kotlin's built-in [lazy] delegate, but with a `reset` method that allows the value to be reset to it's default state (and therefore recomputed
 * upon next access).
 */
fun <T> resettableLazy(initializer: () -> T): ResettableLazy<T> {
  return ResettableLazy(initializer)
}

/**
 * @see resettableLazy
 */
class ResettableLazy<T>(
  val initializer: () -> T
) {
  // We need to distinguish between a lazy value of null and a lazy value that has not been initialized yet
  @Volatile
  private var value: Any? = UNINITIALIZED

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    if (value === UNINITIALIZED) {
      synchronized(this) {
        if (value === UNINITIALIZED) {
          value = initializer()
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  fun reset() {
    value = UNINITIALIZED
  }

  fun isInitialized(): Boolean {
    return value !== UNINITIALIZED
  }

  override fun toString(): String {
    return if (isInitialized()) {
      value.toString()
    } else {
      "Lazy value not initialized yet."
    }
  }

  companion object {
    private val UNINITIALIZED = Any()
  }
}

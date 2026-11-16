/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import org.signal.core.util.Util

class KeyValueDataSet : KeyValueReader {
  private val mutableValues: MutableMap<String, Any?> = HashMap()
  private val types: MutableMap<String, Class<*>> = HashMap()

  val values: Map<String, Any?>
    get() = mutableValues

  fun putBlob(key: String, value: ByteArray?) {
    mutableValues[key] = value
    types[key] = ByteArray::class.java
  }

  fun putBoolean(key: String, value: Boolean) {
    mutableValues[key] = value
    types[key] = Boolean::class.javaObjectType
  }

  fun putFloat(key: String, value: Float) {
    mutableValues[key] = value
    types[key] = Float::class.javaObjectType
  }

  fun putInteger(key: String, value: Int) {
    mutableValues[key] = value
    types[key] = Int::class.javaObjectType
  }

  fun putLong(key: String, value: Long) {
    mutableValues[key] = value
    types[key] = Long::class.javaObjectType
  }

  fun putString(key: String, value: String?) {
    mutableValues[key] = value
    types[key] = String::class.java
  }

  internal fun putAll(other: KeyValueDataSet) {
    mutableValues.putAll(other.mutableValues)
    types.putAll(other.types)
  }

  internal fun removeAll(removes: Collection<String>) {
    for (remove in removes) {
      mutableValues.remove(remove)
      types.remove(remove)
    }
  }

  override fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? {
    return if (containsKey(key)) {
      readNullableValueAsType(key, ByteArray::class.java)
    } else {
      defaultValue
    }
  }

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return if (containsKey(key)) {
      readValueAsType(key, Boolean::class.javaObjectType)
    } else {
      defaultValue
    }
  }

  override fun getFloat(key: String, defaultValue: Float): Float {
    return if (containsKey(key)) {
      readValueAsType(key, Float::class.javaObjectType)
    } else {
      defaultValue
    }
  }

  override fun getInteger(key: String, defaultValue: Int): Int {
    return if (containsKey(key)) {
      readValueAsType(key, Int::class.javaObjectType)
    } else {
      defaultValue
    }
  }

  override fun getLong(key: String, defaultValue: Long): Long {
    return if (containsKey(key)) {
      readValueAsType(key, Long::class.javaObjectType)
    } else {
      defaultValue
    }
  }

  override fun getString(key: String, defaultValue: String?): String? {
    return if (containsKey(key)) {
      readNullableValueAsType(key, String::class.java)
    } else {
      defaultValue
    }
  }

  override fun getNonNullBlob(key: String, defaultValue: ByteArray?): ByteArray {
    return if (containsKey(key)) {
      readValueAsType(key, ByteArray::class.java)
    } else {
      defaultValue ?: throw IllegalArgumentException("No value for $key!")
    }
  }

  override fun getNonNullString(key: String, defaultValue: String?): String {
    return if (containsKey(key)) {
      readValueAsType(key, String::class.java)
    } else {
      defaultValue ?: throw IllegalArgumentException("No value for $key!")
    }
  }

  override fun containsKey(key: String): Boolean {
    return mutableValues.containsKey(key)
  }

  fun getType(key: String): Class<*>? {
    return types[key]
  }

  private fun <E : Any> readNullableValueAsType(key: String, expectedType: Class<E>): E? {
    val value = mutableValues[key] ?: return null
    return castValue(value, expectedType)
  }

  private fun <E : Any> readValueAsType(key: String, expectedType: Class<E>): E {
    val value = mutableValues[key] ?: throw IllegalArgumentException("Nullability mismatch!")
    return castValue(value, expectedType)
  }

  private fun <E : Any> castValue(value: Any, expectedType: Class<E>): E {
    return when {
      value.javaClass == expectedType -> expectedType.cast(value)
      expectedType == Int::class.javaObjectType && value is Long -> expectedType.cast(Util.toIntExact(value))
      expectedType == Long::class.javaObjectType && value is Int -> expectedType.cast(value.toLong())
      else -> throw IllegalArgumentException("Type mismatch!")
    }
  }
}

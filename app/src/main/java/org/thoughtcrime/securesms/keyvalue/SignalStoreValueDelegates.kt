package org.thoughtcrime.securesms.keyvalue

import kotlin.reflect.KProperty

internal fun SignalStoreValues.longValue(key: String, default: Long): SignalStoreValueDelegate<Long> {
  return LongValue(key, default)
}

internal fun SignalStoreValues.booleanValue(key: String, default: Boolean): SignalStoreValueDelegate<Boolean> {
  return BooleanValue(key, default)
}

internal fun <T : String?> SignalStoreValues.stringValue(key: String, default: T): SignalStoreValueDelegate<T> {
  return StringValue(key, default)
}

internal fun SignalStoreValues.integerValue(key: String, default: Int): SignalStoreValueDelegate<Int> {
  return IntValue(key, default)
}

internal fun SignalStoreValues.floatValue(key: String, default: Float): SignalStoreValueDelegate<Float> {
  return FloatValue(key, default)
}

internal fun SignalStoreValues.blobValue(key: String, default: ByteArray): SignalStoreValueDelegate<ByteArray> {
  return BlobValue(key, default)
}

/**
 * Kotlin delegate that serves as a base for all other value types. This allows us to only expose this sealed
 * class to callers and protect the individual implementations as private behind the various extension functions.
 */
sealed class SignalStoreValueDelegate<T> {
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    return getValue(thisRef as SignalStoreValues)
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    setValue(thisRef as SignalStoreValues, value)
  }

  internal abstract fun getValue(values: SignalStoreValues): T
  internal abstract fun setValue(values: SignalStoreValues, value: T)
}

private class LongValue(private val key: String, private val default: Long) : SignalStoreValueDelegate<Long>() {
  override fun getValue(values: SignalStoreValues): Long {
    return values.getLong(key, default)
  }

  override fun setValue(values: SignalStoreValues, value: Long) {
    values.putLong(key, value)
  }
}

private class BooleanValue(private val key: String, private val default: Boolean) : SignalStoreValueDelegate<Boolean>() {
  override fun getValue(values: SignalStoreValues): Boolean {
    return values.getBoolean(key, default)
  }

  override fun setValue(values: SignalStoreValues, value: Boolean) {
    values.putBoolean(key, value)
  }
}

private class StringValue<T : String?>(private val key: String, private val default: T) : SignalStoreValueDelegate<T>() {
  override fun getValue(values: SignalStoreValues): T {
    return values.getString(key, default) as T
  }

  override fun setValue(values: SignalStoreValues, value: T) {
    values.putString(key, value)
  }
}

private class IntValue(private val key: String, private val default: Int) : SignalStoreValueDelegate<Int>() {
  override fun getValue(values: SignalStoreValues): Int {
    return values.getInteger(key, default)
  }

  override fun setValue(values: SignalStoreValues, value: Int) {
    values.putInteger(key, value)
  }
}

private class FloatValue(private val key: String, private val default: Float) : SignalStoreValueDelegate<Float>() {
  override fun getValue(values: SignalStoreValues): Float {
    return values.getFloat(key, default)
  }

  override fun setValue(values: SignalStoreValues, value: Float) {
    values.putFloat(key, value)
  }
}

private class BlobValue(private val key: String, private val default: ByteArray) : SignalStoreValueDelegate<ByteArray>() {
  override fun getValue(values: SignalStoreValues): ByteArray {
    return values.getBlob(key, default)
  }

  override fun setValue(values: SignalStoreValues, value: ByteArray) {
    values.putBlob(key, value)
  }
}

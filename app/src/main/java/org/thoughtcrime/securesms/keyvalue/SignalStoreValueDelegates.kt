package org.thoughtcrime.securesms.keyvalue

import com.squareup.wire.ProtoAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.signal.core.util.LongSerializer
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal fun SignalStoreValues.longValue(key: String, default: Long): SignalStoreValueDelegate<Long> {
  return LongValue(key, default, this.store)
}

internal fun SignalStoreValues.booleanValue(key: String, default: Boolean): SignalStoreValueDelegate<Boolean> {
  return BooleanValue(key, default, this.store)
}

internal fun <T : String?> SignalStoreValues.stringValue(key: String, default: T): SignalStoreValueDelegate<T> {
  return StringValue(key, default, this.store)
}

internal fun SignalStoreValues.integerValue(key: String, default: Int): SignalStoreValueDelegate<Int> {
  return IntValue(key, default, this.store)
}

internal fun SignalStoreValues.floatValue(key: String, default: Float): SignalStoreValueDelegate<Float> {
  return FloatValue(key, default, this.store)
}

internal fun SignalStoreValues.blobValue(key: String, default: ByteArray): SignalStoreValueDelegate<ByteArray> {
  return BlobValue(key, default, this.store)
}

internal fun SignalStoreValues.nullableBlobValue(key: String, default: ByteArray?): SignalStoreValueDelegate<ByteArray?> {
  return NullableBlobValue(key, default, this.store)
}

internal fun <T : Any?> SignalStoreValues.enumValue(key: String, default: T, serializer: LongSerializer<T>): SignalStoreValueDelegate<T> {
  return KeyValueEnumValue(key, default, serializer, this.store)
}

internal fun <M> SignalStoreValues.protoValue(key: String, adapter: ProtoAdapter<M>): SignalStoreValueDelegate<M?> {
  return KeyValueProtoValue(key, adapter, this.store)
}

internal fun <M> SignalStoreValues.protoValue(key: String, default: M, adapter: ProtoAdapter<M>, onSet: ((M) -> Unit)? = null): SignalStoreValueDelegate<M> {
  return KeyValueProtoWithDefaultValue(key, default, adapter, this.store, onSet)
}

internal fun SignalStoreValues.durationValue(key: String, default: Duration?): SignalStoreValueDelegate<Duration?> {
  return DurationValue(key, default, this.store)
}

internal fun <T> SignalStoreValueDelegate<T>.withPrecondition(precondition: () -> Boolean): SignalStoreValueDelegate<T> {
  return PreconditionDelegate(
    delegate = this,
    precondition = precondition
  )
}

internal fun <T> SignalStoreValueDelegate<T>.map(transform: (T) -> T): SignalStoreValueDelegate<T> {
  return MappingDelegate(
    delegate = this,
    transform = transform
  )
}

/**
 * Kotlin delegate that serves as a base for all other value types. This allows us to only expose this sealed
 * class to callers and protect the individual implementations as private behind the various extension functions.
 */
sealed class SignalStoreValueDelegate<T>(val store: KeyValueStore, open val default: T, private val onSet: ((T) -> Unit)? = null) {

  private var flow: Lazy<MutableStateFlow<T>> = lazy { MutableStateFlow(getValue(store)) }

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    return getValue(store)
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    setValue(store, value)
    onSet?.invoke(value)
    if (flow.isInitialized()) {
      flow.value.tryEmit(value)
    }
  }

  fun toFlow(): Flow<T> {
    return flow.value
  }

  internal abstract fun getValue(values: KeyValueStore): T
  internal abstract fun setValue(values: KeyValueStore, value: T)
}

private class LongValue(private val key: String, default: Long, store: KeyValueStore) : SignalStoreValueDelegate<Long>(store, default) {
  override fun getValue(values: KeyValueStore): Long {
    return values.getLong(key, default)
  }

  override fun setValue(values: KeyValueStore, value: Long) {
    values.beginWrite().putLong(key, value).apply()
  }
}

private class BooleanValue(private val key: String, default: Boolean, store: KeyValueStore) : SignalStoreValueDelegate<Boolean>(store, default) {
  override fun getValue(values: KeyValueStore): Boolean {
    return values.getBoolean(key, default)
  }

  override fun setValue(values: KeyValueStore, value: Boolean) {
    values.beginWrite().putBoolean(key, value).apply()
  }
}

private class StringValue<T : String?>(private val key: String, default: T, store: KeyValueStore) : SignalStoreValueDelegate<T>(store, default) {
  override fun getValue(values: KeyValueStore): T {
    @Suppress("UNCHECKED_CAST")
    return values.getString(key, default) as T
  }

  override fun setValue(values: KeyValueStore, value: T) {
    values.beginWrite().putString(key, value).apply()
  }
}

private class IntValue(private val key: String, default: Int, store: KeyValueStore) : SignalStoreValueDelegate<Int>(store, default) {
  override fun getValue(values: KeyValueStore): Int {
    return values.getInteger(key, default)
  }

  override fun setValue(values: KeyValueStore, value: Int) {
    values.beginWrite().putInteger(key, value).apply()
  }
}

private class FloatValue(private val key: String, default: Float, store: KeyValueStore) : SignalStoreValueDelegate<Float>(store, default) {
  override fun getValue(values: KeyValueStore): Float {
    return values.getFloat(key, default)
  }

  override fun setValue(values: KeyValueStore, value: Float) {
    values.beginWrite().putFloat(key, value).apply()
  }
}

private class BlobValue(private val key: String, default: ByteArray, store: KeyValueStore) : SignalStoreValueDelegate<ByteArray>(store, default) {
  override fun getValue(values: KeyValueStore): ByteArray {
    return values.getBlob(key, default)
  }

  override fun setValue(values: KeyValueStore, value: ByteArray) {
    values.beginWrite().putBlob(key, value).apply()
  }
}

private class NullableBlobValue(private val key: String, default: ByteArray?, store: KeyValueStore) : SignalStoreValueDelegate<ByteArray?>(store, default) {
  override fun getValue(values: KeyValueStore): ByteArray? {
    return values.getBlob(key, default)
  }

  override fun setValue(values: KeyValueStore, value: ByteArray?) {
    values.beginWrite().putBlob(key, value).apply()
  }
}

private class DurationValue(private val key: String, default: Duration?, store: KeyValueStore) : SignalStoreValueDelegate<Duration?>(store, default) {
  companion object {
    private const val UNSET: Long = -1
  }

  override fun getValue(values: KeyValueStore): Duration? {
    return values.getLong(key, default?.inWholeNanoseconds ?: UNSET).takeUnless { it == UNSET }?.nanoseconds
  }

  override fun setValue(values: KeyValueStore, value: Duration?) {
    values.beginWrite().putLong(key, value?.inWholeNanoseconds ?: UNSET).apply()
  }
}

private class KeyValueProtoWithDefaultValue<M>(
  private val key: String,
  default: M,
  private val adapter: ProtoAdapter<M>,
  store: KeyValueStore,
  onSet: ((M) -> Unit)? = null
) : SignalStoreValueDelegate<M>(store, default, onSet) {
  override fun getValue(values: KeyValueStore): M {
    return if (values.containsKey(key)) {
      adapter.decode(values.getBlob(key, null))
    } else {
      default
    }
  }

  override fun setValue(values: KeyValueStore, value: M) {
    if (value != null) {
      values.beginWrite().putBlob(key, adapter.encode(value)).apply()
    } else {
      values.beginWrite().remove(key).apply()
    }
  }
}

private class KeyValueProtoValue<M>(
  private val key: String,
  private val adapter: ProtoAdapter<M>,
  store: KeyValueStore
) : SignalStoreValueDelegate<M?>(store, null) {
  override fun getValue(values: KeyValueStore): M? {
    return if (values.containsKey(key)) {
      adapter.decode(values.getBlob(key, null))
    } else {
      null
    }
  }

  override fun setValue(values: KeyValueStore, value: M?) {
    if (value != null) {
      values.beginWrite().putBlob(key, adapter.encode(value)).apply()
    } else {
      values.beginWrite().remove(key).apply()
    }
  }
}

private class KeyValueEnumValue<T>(private val key: String, default: T, private val serializer: LongSerializer<T>, store: KeyValueStore) : SignalStoreValueDelegate<T>(store, default) {
  override fun getValue(values: KeyValueStore): T {
    return if (values.containsKey(key)) {
      serializer.deserialize(values.getLong(key, 0))
    } else {
      default
    }
  }

  override fun setValue(values: KeyValueStore, value: T) {
    values.beginWrite().putLong(key, serializer.serialize(value)).apply()
  }
}

private class PreconditionDelegate<T>(
  private val delegate: SignalStoreValueDelegate<T>,
  private val precondition: () -> Boolean
) : SignalStoreValueDelegate<T>(delegate.store, delegate.default) {

  override fun getValue(values: KeyValueStore): T {
    return if (precondition()) {
      delegate.getValue(values)
    } else {
      delegate.default
    }
  }

  override fun setValue(values: KeyValueStore, value: T) {
    if (precondition()) {
      delegate.setValue(values, value)
    }
  }
}

private class MappingDelegate<T>(
  private val delegate: SignalStoreValueDelegate<T>,
  private val transform: (T) -> T
) : SignalStoreValueDelegate<T>(delegate.store, delegate.default) {

  override fun getValue(values: KeyValueStore): T {
    return transform(delegate.getValue(values))
  }

  override fun setValue(values: KeyValueStore, value: T) {
    delegate.setValue(values, value)
  }
}

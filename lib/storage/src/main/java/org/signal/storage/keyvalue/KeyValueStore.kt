/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor

/**
 * A replacement for [android.content.SharedPreferences] that stores key-value pairs in our
 * encrypted database.
 *
 * Implemented as a write-through cache that is safe to read and write to on the main thread.
 *
 * Writes are enqueued on a separate executor, but writes are finished up in
 * the uncaught exception handler, meaning all writes should finish barring a native crash
 * or the system killing us unexpectedly (i.e. a force-stop).
 */
class KeyValueStore @VisibleForTesting constructor(
  private val storage: KeyValuePersistentStorage,
  private val executor: Executor
) : KeyValueReader {

  constructor(storage: KeyValuePersistentStorage) : this(storage, SignalExecutors.newCachedSingleThreadExecutor("signal-KeyValueStore", ThreadUtil.PRIORITY_BACKGROUND_THREAD))

  private var dataSet: KeyValueDataSet? = null

  private val changeListeners = CopyOnWriteArrayList<KeyedChangeListener>()

  @AnyThread
  @Synchronized
  override fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? {
    return loadedDataSet().getBlob(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return loadedDataSet().getBoolean(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getFloat(key: String, defaultValue: Float): Float {
    return loadedDataSet().getFloat(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getInteger(key: String, defaultValue: Int): Int {
    return loadedDataSet().getInteger(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getLong(key: String, defaultValue: Long): Long {
    return loadedDataSet().getLong(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getString(key: String, defaultValue: String?): String? {
    return loadedDataSet().getString(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getNonNullBlob(key: String, defaultValue: ByteArray?): ByteArray {
    return loadedDataSet().getNonNullBlob(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun getNonNullString(key: String, defaultValue: String?): String {
    return loadedDataSet().getNonNullString(key, defaultValue)
  }

  @AnyThread
  @Synchronized
  override fun containsKey(key: String): Boolean {
    return loadedDataSet().containsKey(key)
  }

  /**
   * @return A writer that allows writing and removing multiple entries in a single atomic
   *         transaction.
   */
  @AnyThread
  fun beginWrite(): Writer {
    return Writer()
  }

  /**
   * @return A reader that lets you read from an immutable snapshot of the store, ensuring that data
   *         is consistent between reads. If you're only reading a single value, it is more
   *         efficient to use the various get* methods instead.
   */
  @AnyThread
  @Synchronized
  fun beginRead(): KeyValueReader {
    val copy = KeyValueDataSet()
    copy.putAll(loadedDataSet())
    return copy
  }

  /**
   * Ensures that any pending writes (such as those made via [Writer.apply]) are finished.
   */
  @AnyThread
  @Synchronized
  fun blockUntilAllWritesFinished() {
    val latch = CountDownLatch(1)

    executor.execute { latch.countDown() }

    try {
      latch.await()
    } catch (e: InterruptedException) {
      Log.w(TAG, "Failed to wait for all writes.")
    }
  }

  /**
   * Registers [listener] to be told whenever a write changes the value of [key], where an absent key is treated as [defaultValue].
   * It is called synchronously on the writing thread with the new value, after the in-memory cache has been updated.
   */
  @AnyThread
  fun <T> addChangeListener(key: String, defaultValue: T, listener: ChangeListener<T>) {
    addChangeListener(key, defaultValue, { it }, listener)
  }

  /**
   * Like [addChangeListener], but the stored value is passed through [mapper] before being handed to [listener].
   * Changes are still detected on the stored value, so [mapper] only runs when the stored value actually changes.
   */
  @AnyThread
  fun <S, T> addChangeListener(key: String, defaultValue: S, mapper: (S) -> T, listener: ChangeListener<T>) {
    @Suppress("UNCHECKED_CAST")
    changeListeners += KeyedChangeListener(key, defaultValue, mapper as (Any?) -> Any?, listener as ChangeListener<Any?>)
  }

  @AnyThread
  fun removeChangeListener(listener: ChangeListener<*>) {
    changeListeners.removeAll { it.listener == listener }
  }

  /**
   * Forces the store to re-fetch all of its data from the database.
   */
  @Synchronized
  fun resetCache() {
    dataSet = null
    loadedDataSet()
  }

  private fun write(newDataSet: KeyValueDataSet, removes: Collection<String>) {
    val changes: List<Change> = synchronized(this) {
      val current = loadedDataSet()
      val changes = findChanges(current, newDataSet, removes)

      current.putAll(newDataSet)
      current.removeAll(removes)

      executor.execute { storage.writeDataSet(newDataSet, removes) }

      changes
    }

    for (change in changes) {
      change.keyedListener.notify(change.value)
    }
  }

  private fun findChanges(current: KeyValueDataSet, newDataSet: KeyValueDataSet, removes: Collection<String>): List<Change> {
    if (changeListeners.isEmpty()) {
      return emptyList()
    }

    val currentValues = current.values
    val newValues = newDataSet.values

    return changeListeners.mapNotNull { keyedListener ->
      val key = keyedListener.key

      val newValue = when {
        newValues.containsKey(key) -> {
          newValues[key]
        }
        key in removes -> {
          keyedListener.defaultValue
        }
        else -> {
          return@mapNotNull null
        }
      }

      val oldValue = if (currentValues.containsKey(key)) {
        currentValues[key]
      } else {
        keyedListener.defaultValue
      }

      if (valuesEqual(oldValue, newValue)) {
        null
      } else {
        Change(keyedListener, newValue)
      }
    }
  }

  private fun valuesEqual(a: Any?, b: Any?): Boolean {
    return when {
      a is ByteArray && b is ByteArray -> {
        a.contentEquals(b)
      }
      (a is Int || a is Long) && (b is Int || b is Long) -> {
        (a as Number).toLong() == (b as Number).toLong()
      }
      else -> {
        a == b
      }
    }
  }

  private fun loadedDataSet(): KeyValueDataSet {
    return dataSet ?: storage.getDataSet().also { dataSet = it }
  }

  inner class Writer {
    private val dataSet = KeyValueDataSet()
    private val removes: MutableSet<String> = HashSet()

    fun putBlob(key: String, value: ByteArray?): Writer {
      dataSet.putBlob(key, value)
      return this
    }

    fun putBoolean(key: String, value: Boolean): Writer {
      dataSet.putBoolean(key, value)
      return this
    }

    fun putFloat(key: String, value: Float): Writer {
      dataSet.putFloat(key, value)
      return this
    }

    fun putInteger(key: String, value: Int): Writer {
      dataSet.putInteger(key, value)
      return this
    }

    fun putLong(key: String, value: Long): Writer {
      dataSet.putLong(key, value)
      return this
    }

    fun putString(key: String, value: String?): Writer {
      dataSet.putString(key, value)
      return this
    }

    fun remove(key: String): Writer {
      removes.add(key)
      return this
    }

    @AnyThread
    fun apply() {
      for (key in removes) {
        check(!dataSet.containsKey(key)) { "Tried to remove a key while also setting it!" }
      }

      write(dataSet, removes)
    }

    @WorkerThread
    fun commit() {
      apply()
      blockUntilAllWritesFinished()
    }
  }

  fun interface ChangeListener<T> {
    fun onChanged(value: T)
  }

  private class KeyedChangeListener(
    val key: String,
    val defaultValue: Any?,
    val mapper: (Any?) -> Any?,
    val listener: ChangeListener<Any?>
  ) {
    fun notify(value: Any?) {
      listener.onChanged(mapper(value))
    }
  }

  private class Change(val keyedListener: KeyedChangeListener, val value: Any?)

  companion object {
    private val TAG = Log.tag(KeyValueStore::class.java)
  }
}

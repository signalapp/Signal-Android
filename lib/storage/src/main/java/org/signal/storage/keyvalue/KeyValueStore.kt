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
   * Forces the store to re-fetch all of its data from the database.
   */
  @Synchronized
  fun resetCache() {
    dataSet = null
    loadedDataSet()
  }

  @Synchronized
  private fun write(newDataSet: KeyValueDataSet, removes: Collection<String>) {
    val current = loadedDataSet()

    current.putAll(newDataSet)
    current.removeAll(removes)

    executor.execute { storage.writeDataSet(newDataSet, removes) }
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

  companion object {
    private val TAG = Log.tag(KeyValueStore::class.java)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Before
import org.junit.Test
import org.signal.storage.protos.SignalStoreList

class KeyValueStoreChangeListenerTest {

  private lateinit var store: KeyValueStore
  private val changes = mutableListOf<Any?>()
  private val listener = KeyValueStore.ChangeListener<Any?> { changes += it }

  @Before
  fun setUp() {
    val backing = KeyValueDataSet()
    val storage = object : KeyValuePersistentStorage {
      override fun writeDataSet(dataSet: KeyValueDataSet, removes: Collection<String>) {
        backing.putAll(dataSet)
        backing.removeAll(removes)
      }

      override fun getDataSet(): KeyValueDataSet = KeyValueDataSet().apply { putAll(backing) }
    }

    store = KeyValueStore(storage) { it.run() }
  }

  @Test
  fun `notifies with the new value when a watched key changes`() {
    store.addChangeListener("a", false, listener)

    store.beginWrite().putBoolean("a", true).apply()

    assertThat(changes).containsExactly(true)
  }

  @Test
  fun `does not notify when an absent key is written with its default`() {
    store.addChangeListener("a", false, listener)

    store.beginWrite().putBoolean("a", false).apply()

    assertThat(changes).isEmpty()
  }

  @Test
  fun `does not notify when the value is unchanged`() {
    store.addChangeListener("a", null, listener)
    store.beginWrite().putString("a", "x").apply()
    changes.clear()

    store.beginWrite().putString("a", "x").apply()

    assertThat(changes).isEmpty()
  }

  @Test
  fun `compares blobs by content`() {
    store.addChangeListener("a", null, listener)
    store.beginWrite().putBlob("a", byteArrayOf(1, 2)).apply()
    changes.clear()

    store.beginWrite().putBlob("a", byteArrayOf(1, 2)).apply()
    assertThat(changes).isEmpty()

    store.beginWrite().putBlob("a", byteArrayOf(3)).apply()
    assertThat(changes.map { (it as ByteArray).toList() }).containsExactly(listOf<Byte>(3))
  }

  @Test
  fun `does not notify for unwatched keys`() {
    store.addChangeListener("a", 0, listener)

    store.beginWrite().putInteger("b", 1).apply()

    assertThat(changes).isEmpty()
  }

  @Test
  fun `notifies each watched key once in a single write`() {
    store.addChangeListener("a", 0L, listener)
    store.addChangeListener("b", 0L, listener)

    store.beginWrite().putLong("a", 1).putLong("b", 2).putLong("c", 3).apply()

    assertThat(changes).containsExactly(1L, 2L)
  }

  @Test
  fun `notifies with the default when a non-default value is removed`() {
    store.addChangeListener("a", 7, listener)

    store.beginWrite().remove("a").apply()
    assertThat(changes).isEmpty()

    store.beginWrite().putInteger("a", 1).apply()
    changes.clear()

    store.beginWrite().remove("a").apply()
    assertThat(changes).containsExactly(7)
  }

  @Test
  fun `passes the new value through the mapper`() {
    store.addChangeListener("a", 0, { it * 10 }, listener)

    store.beginWrite().putInteger("a", 2).apply()

    assertThat(changes).containsExactly(20)
  }

  @Test
  fun `does not run the mapper when the value is unchanged`() {
    var mapperCalls = 0
    store.addChangeListener("a", 0, { mapperCalls++; it }, listener)

    store.beginWrite().putInteger("a", 0).apply()

    assertThat(mapperCalls).isEqualTo(0)
  }

  @Test
  fun `stops notifying a mapped listener after removal`() {
    store.addChangeListener("a", 0, { it * 10 }, listener)
    store.removeChangeListener(listener)

    store.beginWrite().putInteger("a", 2).apply()

    assertThat(changes).isEmpty()
  }

  @Test
  fun `decodes protos with the adapter and falls back to the default`() {
    val default = SignalStoreList(contents = listOf("default"))
    values().addChangeListener("a", default, SignalStoreList.ADAPTER) { changes += it }

    store.beginWrite().putBlob("a", SignalStoreList(contents = listOf("x")).encode()).apply()
    store.beginWrite().remove("a").apply()

    assertThat(changes).containsExactly(SignalStoreList(contents = listOf("x")), default)
  }

  @Test
  fun `stops notifying after removal`() {
    store.addChangeListener("a", false, listener)
    store.removeChangeListener(listener)

    store.beginWrite().putBoolean("a", true).apply()

    assertThat(changes).isEmpty()
  }

  private fun values(): SignalStoreValues {
    return object : SignalStoreValues(store) {
      override fun onFirstEverAppLaunch() = Unit
      override val keysToIncludeInBackup: List<String> = emptyList()
    }
  }
}
